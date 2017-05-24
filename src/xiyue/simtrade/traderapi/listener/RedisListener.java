package xiyue.simtrade.traderapi.listener;

import java.util.Map;

/**
 * Created by chenyan on 2017/5/24.
 */
public class RedisListener implements XiyueListener {
    @Override
    public void handEvent(XiyueEvent e) {
        String function = e.getEventFunction();
        Map<String, Object> fields = e.getFields();
        switch (function){
            case "ReqOrderAction" :
                this.afterReqOrderAction(fields);
                break;
            case "ReqOrderInsert" :
                this.afterReqOrderInsert(fields);
                break;
            default:
                break;
        }
    }

    private void afterReqOrderAction(Map<String , Object> fields){
        System.out.println(fields);
    }

    private void afterReqOrderInsert(Map<String , Object> fields){
        System.out.println(fields);
    }
}
