package xiyue.simtrade.traderapi;

/**
 * Created by chenyan on 2017/5/17.
 */
public abstract class TradeFactory {
    public abstract TradeService getInstance(String userId, String sessionId);
}
