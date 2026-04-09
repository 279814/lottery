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
        //TODO
        return null;
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
