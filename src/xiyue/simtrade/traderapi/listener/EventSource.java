package xiyue.simtrade.traderapi.listener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by chenyan on 2017/5/24.
 * 事件源
 */
public class EventSource {
    // 保存监听器引用
    private List<XiyueListener> listeners = Collections.synchronizedList(new ArrayList<>());
    // 注册监听器
    public void addListener(XiyueListener listener){
        listeners.add(listener);
    }
    public synchronized void handleEvent(Map<String, Object> fields , String eventFunction){
        XiyueEvent e = new XiyueEvent(this);
        e.setFields(fields);
        fieldsChanged(e);
    }
    private void fieldsChanged(XiyueEvent e){
        listeners.stream().forEach(listener -> listener.handEvent(e));
    }
}
