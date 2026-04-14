package com.itheima.prize.api.action;

import com.alibaba.fastjson.JSON;
import com.itheima.prize.api.config.LuaScript;
import com.itheima.prize.commons.config.RabbitKeys;
import com.itheima.prize.commons.config.RedisKeys;
import com.itheima.prize.commons.db.entity.*;
import com.itheima.prize.commons.db.mapper.CardGameMapper;
import com.itheima.prize.commons.db.service.CardGameService;
import com.itheima.prize.commons.utils.ApiResult;
import com.itheima.prize.commons.utils.RedisUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.models.auth.In;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.text.SimpleDateFormat;
import java.util.*;

@RestController
@RequestMapping("/api/act")
@Api(tags = {"抽奖模块"})
public class ActController {

    @Autowired
    private RedisUtil redisUtil;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private LuaScript luaScript;

    @GetMapping("/go/{gameid}")
    @ApiOperation(value = "抽奖")
    @ApiImplicitParams({
            @ApiImplicitParam(name="gameid",value = "活动id",example = "1",required = true)
    })
    public ApiResult<Object> act(@PathVariable int gameid, HttpServletRequest request){
        //获取活动信息
        Object game = redisUtil.get(RedisKeys.INFO + gameid);
        if (game == null) {
            return new ApiResult(-1,"活动未开始",null);
        }
        CardGame gameInfo = (CardGame) game;
        //判断活动是否开始
        if (gameInfo.getStarttime().getTime() > System.currentTimeMillis()) {
            return new ApiResult(-1,"活动未开始",null);
        }
        //判断活动是否结束
        if (gameInfo.getEndtime().getTime() < System.currentTimeMillis()) {
            return new ApiResult(-1,"活动已结束",null);
        }
        //判断用户是否登录
        HttpSession session = request.getSession();
        Object user = session.getAttribute("user");
        if (user == null) {
            return new ApiResult(-1,"未登录",null);
        }

        CardUser userInfo = (CardUser) user;
        if(redisUtil.setNx(RedisKeys.USERGAME + gameid + "_" + userInfo.getId(), 1)){
            //mq异步通知
            CardUserGame cardUserGame = new CardUserGame();
            cardUserGame.setUserid(userInfo.getId());
            cardUserGame.setGameid(gameid);
            cardUserGame.setCreatetime(new Date());
            //RabbitMq传输对象的时候，可以使用FastJson将对象转为字符串后传输
            String message = JSON.toJSONString(cardUserGame);
            rabbitTemplate.convertAndSend(RabbitKeys.EXCHANGE_DIRECT,RabbitKeys.QUEUE_PLAY,message);
        }
        //获取用户level
        Integer level = userInfo.getLevel();
        //获取活动策略
        Integer maxGoal = (Integer)redisUtil.hget(RedisKeys.MAXGOAL + gameid, level + "");
        Integer maxEnter = (Integer)redisUtil.hget(RedisKeys.MAXENTER + gameid, level + "");
        if(!redisUtil.hasKey(RedisKeys.USERENTER + gameid + "_" + userInfo.getId())){
            //活动当前还有多少时间
            long enterTime = gameInfo.getEndtime().getTime() - System.currentTimeMillis();
            //转成秒
            long enterTimeSeconds = enterTime / 1000;
            redisUtil.set(RedisKeys.USERENTER + gameid + "_" + userInfo.getId(), 0, enterTimeSeconds + 1);
            redisUtil.set(RedisKeys.USERHIT + gameid + "_" + userInfo.getId(), 0, enterTimeSeconds + 1);
        }
        //判断抽奖次数
        if(maxGoal != 0){
            //判断用户是否超过最大抽奖次数
            if((Integer)redisUtil.get(RedisKeys.USERENTER + gameid + "_" + userInfo.getId()) >= maxEnter){
                return new ApiResult(-1,"您的抽奖次数已用完",null);
            }
        }
        //判断中奖次数
        if(maxGoal != 0){
            //判断用户是否超过最大中奖次数
            if((Integer)redisUtil.get(RedisKeys.USERHIT + gameid + "_" + userInfo.getId()) >= maxGoal){
                return new ApiResult(-1,"您已达到最大中奖数",null);
            }
        }


        Long token = luaScript.tokenCheck(RedisKeys.TOKENS+gameid,String.valueOf(new Date().getTime()));
        if(token == 0){
            //抽奖次数+1
            redisUtil.incr(RedisKeys.USERENTER + gameid + "_" + userInfo.getId(), 1);
            return new ApiResult(-1,"奖品已抽光",null);
        }else if(token == 1){
            redisUtil.incr(RedisKeys.USERENTER + gameid + "_" + userInfo.getId(), 1);
            return new ApiResult(0,"未中奖",null);
        }else{
            //token有效，中奖！
            redisUtil.incr(RedisKeys.USERENTER + gameid + "_" + userInfo.getId(), 1);
            redisUtil.incr(RedisKeys.USERHIT + gameid + "_" + userInfo.getId(), 1);
            //奖品信息
            CardProduct cardProduct = (CardProduct)redisUtil.get(RedisKeys.TOKEN + gameid + "_" + token);

            //mq
            CardUserHit cardUserHit = new CardUserHit();
            cardUserHit.setUserid(userInfo.getId());
            cardUserHit.setGameid(gameid);
            cardUserHit.setProductid(cardProduct.getId());
            cardUserHit.setHittime(new Date());
            String msg = JSON.toJSONString(cardUserHit);
            rabbitTemplate.convertAndSend(RabbitKeys.EXCHANGE_DIRECT,RabbitKeys.QUEUE_HIT,msg);

            return new ApiResult(1,"恭喜中奖",cardProduct);
        }

    }

    @GetMapping("/info/{gameid}")
    @ApiOperation(value = "缓存信息")
    @ApiImplicitParams({
            @ApiImplicitParam(name="gameid",value = "活动id",example = "1",required = true)
    })
    public ApiResult info(@PathVariable int gameid){
        //可以将所有本活动相关的信息放在一个Map中集中返回，方便swagger中查看
        Map<String,Object> map = new HashMap<>();
        map.put(RedisKeys.INFO+gameid,redisUtil.get(RedisKeys.INFO+gameid));

        List<Object> tokenList = redisUtil.lrange(RedisKeys.TOKENS + gameid,0,-1);
        Map<String, Object> tokenMap = new HashMap<>();
        if (tokenList != null && !tokenList.isEmpty()) {
            for (Object token : tokenList){
                try {
                    String tokenKey = RedisKeys.TOKEN + gameid + "_" + token;
                    Object tokenValue = redisUtil.get(tokenKey);
                    if (tokenValue != null) {
                        long tokenTimestamp = Long.parseLong(token.toString()) / 1000;
                        Date tokenDate = new Date(tokenTimestamp);
                        String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(tokenDate);
                        tokenMap.put(dateStr, tokenValue);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        map.put(RedisKeys.TOKENS+gameid, tokenMap);

        map.put(RedisKeys.MAXGOAL+gameid, redisUtil.hmget(RedisKeys.MAXGOAL+gameid));
        map.put(RedisKeys.MAXENTER+gameid, redisUtil.hmget(RedisKeys.MAXENTER+gameid));
        map.put(RedisKeys.RANDOMRATE+gameid, redisUtil.hmget(RedisKeys.RANDOMRATE+gameid));
        map.put("now",new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date()));

        return new ApiResult(200,"缓存信息",map);
    }
}