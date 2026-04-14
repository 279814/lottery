package com.itheima.prize.msg;

import com.alibaba.fastjson.JSON;
import com.itheima.prize.commons.config.RabbitKeys;
import com.itheima.prize.commons.db.entity.*;
import com.itheima.prize.commons.db.mapper.CardUserHitMapper;
import com.itheima.prize.commons.db.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RabbitListener(queues = RabbitKeys.QUEUE_HIT)
public class PrizeHitReceiver {
    private final static Logger logger = LoggerFactory.getLogger(PrizeHitReceiver.class);

    @Autowired
    private CardUserHitService hitService;
//    @Autowired
//    private ViewCardUserHitService viewHitService;
//    @Autowired
//    private CardUserService userService;
//    @Autowired
//    private CardGameService gameService;
//    @Autowired
//    private CardProductService productService;

    @RabbitHandler
    @Transactional(rollbackFor = Exception.class)
    public void processMessage(String message) {
        logger.info("user hit : message={}", message);
        CardUserHit cardUserHit = JSON.parseObject(message, CardUserHit.class);
        hitService.save(cardUserHit);
        
//        ViewCardUserHit viewCardUserHit = new ViewCardUserHit();
//        viewCardUserHit.setGameid(cardUserHit.getGameid());
//        viewCardUserHit.setUserid(cardUserHit.getUserid());
//        viewCardUserHit.setProductid(cardUserHit.getProductid());
//        viewCardUserHit.setHittime(cardUserHit.getHittime());
//
//        CardGame game = gameService.getById(cardUserHit.getGameid());
//        if (game != null) {
//            viewCardUserHit.setTitle(game.getTitle());
//            viewCardUserHit.setType(String.valueOf(game.getType()));
//        }
//
//        CardUser user = userService.getById(cardUserHit.getUserid());
//        if (user != null) {
//            viewCardUserHit.setUname(user.getUname());
//            viewCardUserHit.setRealname(user.getRealname());
//            viewCardUserHit.setIdcard(user.getIdcard());
//            viewCardUserHit.setPhone(user.getPhone());
//            viewCardUserHit.setLevel(String.valueOf(user.getLevel()));
//        }
//
//        CardProduct product = productService.getById(cardUserHit.getProductid());
//        if (product != null) {
//            viewCardUserHit.setName(product.getName());
//            viewCardUserHit.setPrice(product.getPrice());
//        }
//
//        viewHitService.save(viewCardUserHit);
    }
}