package com.itheima.prize.msg;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.itheima.prize.commons.config.RedisKeys;
import com.itheima.prize.commons.db.entity.*;
import com.itheima.prize.commons.db.service.CardGameProductService;
import com.itheima.prize.commons.db.service.CardGameRulesService;
import com.itheima.prize.commons.db.service.CardGameService;
import com.itheima.prize.commons.db.service.GameLoadService;
import com.itheima.prize.commons.utils.RedisUtil;
import org.apache.commons.lang3.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 活动信息预热，每隔1分钟执行一次
 * 查找未来1分钟内（含），要开始的活动
 */
@Component
public class GameTask {
    private final static Logger log = LoggerFactory.getLogger(GameTask.class);
    @Autowired
    private CardGameService gameService;
    @Autowired
    private CardGameProductService gameProductService;
    @Autowired
    private CardGameRulesService gameRulesService;
    @Autowired
    private GameLoadService gameLoadService;
    @Autowired
    private RedisUtil redisUtil;

    @Scheduled(cron = "0 * * * * ?")
    public void execute() {

//        每分钟扫描一遍card_game表
        List<CardGame> gameList = gameService.lambdaQuery()
                .gt(CardGame::getStarttime,new Date())
                .lt(CardGame::getStarttime, DateUtils.addMinutes(new Date(),1))
                .list();
        if (gameList != null && gameList.size() > 0) {
            for (CardGame game : gameList) {
                log.info("开始预热活动：{}",game.getTitle());
                game.setStatus(1);
                //1）活动基本信息
                // k-v，以活动id为key，活动对象为value，永不超时
                //redisUtil.set(RedisKeys.INFO+game.getId(),game,-1);
                redisUtil.set(RedisKeys.INFO+game.getId(),game,-1);

                //2）活动策略信息
                //hset，以活动id为group，用户等级为key，策略值为value
                //redisUtil.hset(RedisKeys.MAXGOAL + game.getId(),r.getUserlevel()+"",r.getGoalTimes());
                //redisUtil.hset(RedisKeys.MAXENTER + game.getId(),r.getUserlevel()+"",r.getEnterTimes());
                List<CardGameRules> rules = gameRulesService.lambdaQuery()
                        .eq(CardGameRules::getGameid,game.getId())
                        .list();
                for (CardGameRules r : rules) {
                    redisUtil.hset(RedisKeys.MAXGOAL + game.getId(),r.getUserlevel()+"",r.getGoalTimes());
                    redisUtil.hset(RedisKeys.MAXENTER + game.getId(),r.getUserlevel()+"",r.getEnterTimes());
                    redisUtil.hset(RedisKeys.RANDOMRATE + game.getId(),r.getUserlevel()+"",r.getRandomRate());
                }
                redisUtil.expire(RedisKeys.MAXGOAL + game.getId(),(game.getEndtime().getTime() - new Date().getTime()) / 1000);
                redisUtil.expire(RedisKeys.MAXENTER + game.getId(),(game.getEndtime().getTime() - new Date().getTime()) / 1000);
                redisUtil.expire(RedisKeys.RANDOMRATE + game.getId(),(game.getEndtime().getTime() - new Date().getTime()) / 1000);

                //3）抽奖令牌桶
                //双端队列，以活动id为key，在活动时间段内，随机生成时间戳做令牌，有多少个奖品就生成多少个令牌。令牌即奖品发放的时间点。从小到大排序后从右侧入队。
                //redisUtil.rightPushAll(RedisKeys.TOKENS + game.getId(),tokenList);
                // 4）奖品映射信息
                //k-v , 以活动id_令牌为key，奖品信息为value，会员获取到令牌后，如果令牌有效，则用令牌token值，来这里获取奖品详细信息
                //redisUtil.set(RedisKeys.TOKEN + game.getId() +"_"+token,cardProduct,expire);
                //活动奖品信息
                long start = game.getStarttime().getTime();
                //活动结束时间
                long end = game.getEndtime().getTime();
                //计算活动结束时间到现在还有多少秒，作为redis key过期时间
                long expire = (end - new Date().getTime())/1000;
//            long expire = -1; //永不过期
                //活动持续时间（ms）
                long duration = end - start;

                Map queryMap = new HashMap();
                queryMap.put("gameid",game.getId());

                List<CardProductDto> products = gameLoadService.getByGameId(game.getId());
                Map<Integer,CardProduct> productMap = new HashMap<>(products.size());
                products.forEach(p -> productMap.put(p.getId(),p));
                log.info("load product type:{}",productMap.size());

                //奖品数量等配置信息
                List<CardGameProduct> gameProducts = gameProductService.listByMap(queryMap);
                log.info("load bind product:{}",gameProducts.size());

                //令牌桶
                List<Long> tokenList = new ArrayList();
                gameProducts.forEach(cgp ->{
                    //生成amount个start到end之间的随机时间戳做令牌
                    for (int i = 0; i < cgp.getAmount(); i++) {
                        long rnd = start + new Random().nextInt((int)duration);
                        //为什么乘1000，再额外加一个随机数呢？ - 防止时间段奖品多时重复
                        //记得取令牌判断时间时，除以1000，还原真正的时间戳
                        long token = rnd * 1000 + new Random().nextInt(999);
                        //将令牌放入令牌桶
                        tokenList.add(token);
                        //以令牌做key，对应的商品为value，创建redis缓存
                        log.info("token -> game : {} -> {}",token/1000 ,productMap.get(cgp.getProductid()).getName());
                        //token到实际奖品之间建立映射关系
                        redisUtil.set(RedisKeys.TOKEN + game.getId() +"_"+token,productMap.get(cgp.getProductid()),expire);
                    }
                });
                //排序后放入redis队列
                Collections.sort(tokenList);
                log.info("load tokens:{}",tokenList);

                //从右侧压入队列，从左到右，时间戳逐个增大
                redisUtil.rightPushAll(RedisKeys.TOKENS + game.getId(),tokenList);
                redisUtil.expire(RedisKeys.TOKENS + game.getId(),expire);

                game.setStatus(1);
                gameService.updateById(game);
            }
        }
    }
}
