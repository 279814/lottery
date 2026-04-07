package com.itheima.prize.api.action;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.BeanUtils;
import com.itheima.prize.commons.config.RedisKeys;
import com.itheima.prize.commons.db.entity.CardUser;
import com.itheima.prize.commons.db.mapper.CardUserMapper;
import com.itheima.prize.commons.db.service.CardUserService;
import com.itheima.prize.commons.utils.ApiResult;
import com.itheima.prize.commons.utils.PasswordUtil;
import com.itheima.prize.commons.utils.RedisUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.List;

@RestController
@RequestMapping(value = "/api")
@Api(tags = {"登录模块"})
public class LoginController {
    @Autowired
    private CardUserService userService;

    @Autowired
    private RedisUtil redisUtil;

    @PostMapping("/login")
    @ApiOperation(value = "登录")
    @ApiImplicitParams({
            @ApiImplicitParam(name="account",value = "用户名",required = true),
            @ApiImplicitParam(name="password",value = "密码",required = true)
    })
    public ApiResult login(HttpServletRequest request, @RequestParam String account,@RequestParam String password) {
        //进行md5加密
        password = PasswordUtil.encodePassword(password);
        //查询用户
        CardUser user = userService.lambdaQuery()
                .eq(CardUser::getUname,account)
                .eq(CardUser::getPasswd,password)
                .one();
        //查询是否有account_fail，如存在，return
        if(redisUtil.hasKey(account + "_fail")){
            return new ApiResult(0,"密码错误5次，请5分钟后再登录",null);
        }
        //判断是否查询到用户
        //登录失败
        if(user == null){
            //查询在redis里account是否存在，
            if(redisUtil.hasKey(account)){
                // 存在，判断次数是否超过5次，超过5次则锁定用户5分钟，设置一个key:account_fail,5分钟过期，同时删除key:account return
                if((Integer)redisUtil.get(account) >= 5){
                    redisUtil.set(account + "_fail",0,5*60);
                    redisUtil.del(account);
                    return new ApiResult(0,"密码错误5次，请5分钟后再登录",null);
                }
                //存在，不超过5次，则累加次数
                redisUtil.incr(account,1);
            }
            //不存在，则创建一个，value进行累加，key为account，value为次数，过期时间为30分钟
            else {
                redisUtil.set(account,1,30*60);
            }
            return new ApiResult(0,"账户名或密码错误",null);
        }
        //登录成功
        else {
            // 删除redis里的account
            redisUtil.del(account);
            //准备data
            CardUser data = new CardUser();
            BeanUtil.copyProperties(user, data);
            data.setPasswd(null);
            data.setIdcard(null);
            // 设置session
            HttpSession session = request.getSession();
            session.setAttribute("user",data);
            return new ApiResult(1,"登录成功",data);
        }
    }

    @GetMapping("/logout")
    @ApiOperation(value = "退出")
    public ApiResult logout(HttpServletRequest request) {
        HttpSession session = request.getSession();
        session.removeAttribute("user");
        return new ApiResult(1,"退出成功",null);
    }

}