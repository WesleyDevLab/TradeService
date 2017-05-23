package xiyue.simtrade.traderapi;


import xiyue.simtrade.traderapi.impl.TradeServiceImpl;

/**
 * Created by chenyan on 2017/5/17.
 */
public class TradeServiceFactory extends TradeFactory{

    @Override
	public TradeServiceImpl getInstance(String userId, String sessionId) {
        return new TradeServiceImpl(userId, sessionId);
    }

}
