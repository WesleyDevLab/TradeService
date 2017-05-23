package xiyue.simtrade.traderapi.listener;

import java.util.EventListener;

/**
 * Created by chenyan on 2017/5/23.
 */
public interface XiyueListener extends EventListener{
    void handleRedisEvent(RedisEvent e);
    void handleJPushEvent(JPushEvent e);
}
