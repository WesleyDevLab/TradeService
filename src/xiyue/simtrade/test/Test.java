package xiyue.simtrade.test;

import org.apache.log4j.PropertyConfigurator;
import xiyue.simtrade.traderapi.TradeServiceFactory;
import xiyue.simtrade.traderapi.impl.TradeServiceImpl;
import xiyue.simtrade.traderapi.listener.EventSource;
import xiyue.simtrade.traderapi.listener.JPushListener;
import xiyue.simtrade.traderapi.listener.RedisListener;
import xiyue.simtrade.traderapi.vo.ResultJson;

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
        EventSource source = new EventSource();
        source.addListener(new RedisListener());
        source.addListener(new JPushListener());
        tradeService.setSource(source);

        //登陆
        ResultJson resultJson = tradeService.ReqUserLogin(username, password);

//        resultJson = tradeService.ReqUserPasswordUpdate(username,"222222","111111");
//        ResultJson ResultJson json = tradeService.ReqQryTradingAccount();
//        ResultJson json = tradeService.ReqQryPartDepositWithdraw();
//        ResultJson json = tradeService.ReqQryPartPosition();
//        ResultJson json = tradeService.ReqQryWarrantDetail();
//        ResultJson json = tradeService.ReqQryOrder("");
//        ResultJson json = tradeService.ReqQryTrade("");

        ResultJson json = tradeService.ReqOrderAction("20161111111");


        System.out.println(json.toString());
    }
}
