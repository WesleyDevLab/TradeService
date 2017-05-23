package xiyue.simtrade.test;

import org.apache.log4j.PropertyConfigurator;
import xiyue.simtrade.traderapi.TradeServiceFactory;
import xiyue.simtrade.traderapi.impl.TradeServiceImpl;
import xiyue.simtrade.traderapi.listener.JPushEvent;
import xiyue.simtrade.traderapi.listener.RedisEvent;
import xiyue.simtrade.traderapi.listener.XiyueListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by chenyan on 2017/5/22.
 */
public class Test {

    public static void main(String[] args) {
        PropertyConfigurator.configure("E://workspace//eclipse//xytrade//src//main//resources//properties//log4j.properties");
        String username = "600009";
        String password = "111111";
        String session = "1";
        TradeServiceFactory factory = new TradeServiceFactory();
        TradeServiceImpl tradeService = factory.getInstance(username, session);

        //登陆
//        ResultJson resultJson = tradeService.ReqUserLogin(username, password);

//        resultJson = tradeService.ReqUserPasswordUpdate(username,"222222","111111");
//        ResultJson ResultJson json = tradeService.ReqQryTradingAccount();
//        ResultJson json = tradeService.ReqQryPartDepositWithdraw();
//        ResultJson json = tradeService.ReqQryPartPosition();
//        ResultJson json = tradeService.ReqQryWarrantDetail();
//        ResultJson json = tradeService.ReqQryOrder("");
//        ResultJson json = tradeService.ReqQryTrade("");

        tradeService.addListener(new XiyueListener() {
            @Override
            public void handleRedisEvent(RedisEvent e) {
                System.out.println("插入redis" + e.getFields());
            }

            @Override
            public void handleJPushEvent(JPushEvent e) {

            }
        });
        Map<String,Object> map = new HashMap<>();
        map.put("chen","yan");
        tradeService.updateRedis(map);


        tradeService.addListener(new XiyueListener() {
            @Override
            public void handleRedisEvent(RedisEvent e) {
                System.out.println("redis" + e.getFields());
            }

            @Override
            public void handleJPushEvent(JPushEvent e) {

            }
        });
        Map<String,Object> map2 = new HashMap<>();
        map2.put("chen",123);
        tradeService.updateRedis(map2);

//        System.out.println(json.toString());
    }
}
