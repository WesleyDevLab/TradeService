package xiyue.simtrade.traderapi.vo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

/**
 * Created by chenyan on 2017年5月22日 下午3:13:16
 */
public class ResultJson {
	@Setter
	@Getter
	private Boolean isSucc = true; // 是否成功
	@Setter
	@Getter
	private int errorCode ; // 错误码
	@Setter
	@Getter
	private String message ; //消息
	@Setter
	@Getter
	private Boolean bIsLast ;
	@Setter
	@Getter
	private List<Map<String , Object>> rspMapList = new LinkedList<>();
	@Setter
	@Getter
	private Map<String, Object> errMapList = null;

	private static Gson gson = new GsonBuilder().create();

	@Override
	public String toString(){
		Map<String,Object> res = new HashMap<>();
		if(Optional.ofNullable(errMapList).isPresent()){
			setErrorCode((int)getErrMapList().get("ErrorID"));
			setMessage((String)getErrMapList().get("ErrorMsg"));
			setIsSucc(false);
		}
		res.put("issucc", getIsSucc() ? 1 : 0);//0 不成功  1成功
		res.put("errorcode", getErrorCode());
    	res.put("message", getMessage());
    	res.put("datas", getRspMapList());
    	return bean2Json(res);
	}
   private static String bean2Json(Object obj){  
          return gson.toJson(obj);  
  }  
}
