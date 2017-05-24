package xiyue.simtrade.traderapi.listener;

import java.util.EventListener;

/**
 * Created by chenyan on 2017/5/23.
 */
public interface XiyueListener extends EventListener{
    String REQ_ORDER_ACTION = "ReqOrderAction";
    String REQ_ORDER_INSERT = "ReqOrderInsert";
    void handEvent(XiyueEvent e);
}
