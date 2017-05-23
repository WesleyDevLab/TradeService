package xiyue.simtrade.traderapi.impl;

import com.manyit.xytrade.traderapi.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xiyue.simtrade.traderapi.TradeService;
import xiyue.simtrade.traderapi.listener.JPushEvent;
import xiyue.simtrade.traderapi.listener.RedisEvent;
import xiyue.simtrade.traderapi.listener.XiyueListener;
import xiyue.simtrade.traderapi.vo.ResultJson;
import xiyue.simtrade.traderapi.vo.UserInfo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by chenyan on 2017年5月18日 上午10:32:24 
 */
@Slf4j
public class TradeServiceImpl extends TradeService {
    @Getter
    private String userId; //用户ID
    @Getter
    private String sessionId; //sessionID
    @Getter
    private UserInfo userInfo; //用户信息
    @Getter
    private CSsecFtdcTraderApi traderApi; //traderApi
    @Getter
    private Boolean isConnected = false; //是否连接

    private final ReentrantLock apiLock = new ReentrantLock(); // 重入锁
    private final AtomicInteger nRequestID = new AtomicInteger(0);//请求id

	@Getter
	private Map<Integer,ResultJson>  requestMap = new ConcurrentHashMap<>(); // 请求信息集合
    @Getter
    private Map<Integer, CountDownLatch> synLatchMap = new ConcurrentHashMap<>();// 同步消息集合
    
   static{
   		//加载DLL文件
    	loadDll();
    }

    public TradeServiceImpl(String userId, String sessionId) {
        //初始化字段
        this.userId = userId;
        this.sessionId = sessionId;
        //加载TraderApi
        this.initTraderApi();
    }

   //加载DLL文件
   private static void loadDll(){
	    String osName = System.getProperty("os.name");
		String os = osName != null && osName.toLowerCase().contains("windows") ? "windows" : "linux";
//		String root = AppContextServlet.getProperty("trade.root." + os);
        // 后期通过properties获取路径
        String root = "E:\\workspace\\dll\\";
		if (os.equalsIgnoreCase("windows")) {
			System.load(root + "iconv.dll");
			System.load(root + "ssectraderapi.dll");
			System.load(root + "TraderApi.dll");
		} else {
			System.out.println(System.getProperty("java.library.path"));
			log.info("java.library.path===>" + System.getProperty("java.library.path"));
			System.loadLibrary("sfitbase");
			System.loadLibrary("ssectraderapi");
			System.loadLibrary("javauserapi");
		}
   }

    //加载TraderApi
    private void initTraderApi(){
        lock();
        try{
//          String url = AppContextServlet.getProperty("trade.api.url");
            // 后期通过properties获取路径
            String tradeApiUrl = "tcp://112.64.173.253:53024";
            traderApi = CSsecFtdcTraderApi.CreateFtdcTraderApi();
            traderApi.SetHeartbeatTimeout(60000);
            traderApi.RegisterFront(tradeApiUrl);
            traderApi.RegisterSpi(this);
            traderApi.SubscribePrivateTopic(TE_RESUME_TYPE.TERT_RESUME);
            traderApi.Init();
            log.info(" 启动TraderAPI ===> initTraderApi , 用户ID = " + getUserId());
        }finally {
            unlock();
        }

        //检测是否连接成功
        while (!getIsConnected()) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                log.error("initTraderApi ==> Thread.sleep错误", e);
            }
        }
    }

    /**
     * 异步消息存放
     */
    private int setSynLatch(){
        //获取requestId
        int requestId = nRequestID.incrementAndGet();
        CountDownLatch synLatch = new CountDownLatch(1);
        //设置CountDownLatch实例
        synLatchMap.put(requestId , synLatch);
        return requestId;
    }

    /**
     * 异步消息删除
     */
    private void delSynLatch(int nRequestID){
        synLatchMap.remove(nRequestID);
    }

    /**
     * 结果获取完成后，清除信息
     */
    private void afterGetInfoAndDel(int nRequestID){
        this.delResultJsonByRequestID(nRequestID);
        this.delSynLatch(nRequestID);
    }

    private boolean isExistKey(int nRequestID){
        return requestMap.keySet().contains(nRequestID);
    }
    private ResultJson getResultJsonByRequestID(int nRequestID){
        return requestMap.get(nRequestID);
    }
    private void putResultJson(int nRequestID , ResultJson rj){
        requestMap.put(nRequestID, rj);
    }
    private void delResultJsonByRequestID(int nRequestID){
        requestMap.remove(nRequestID);
    }

    /**
     * 处理异步结果数据
     */
    private void handleRspInfo(int nRequestID , Map<String, Object> succRspMap ,Map<String, Object> errRspMap , boolean bIsLast){
        if(isExistKey(nRequestID)){
            ResultJson rj = getResultJsonByRequestID(nRequestID);
            rj.setBIsLast(bIsLast);
            if(Optional.ofNullable(succRspMap).isPresent()){
                rj.getRspMapList().add(succRspMap);
            }else{
                rj.setIsSucc(false);
            }
            rj.setErrMapList(errRspMap);
        }else{
            ResultJson rj = new ResultJson();
            rj.setBIsLast(bIsLast);
            if(Optional.ofNullable(succRspMap).isPresent()){
                rj.getRspMapList().add(succRspMap);
            }else{
                rj.setIsSucc(false);
            }
            rj.setErrMapList(errRspMap);
            putResultJson(nRequestID, rj);
        }
    }

    /**
     * 登陆完成设置用户信息
     */
    private void setUserInfoModule(Map<String, Object> rspMap , String sessionId ){
        String userId = String.valueOf(rspMap.get("UserID"));
        String tradingday = String.valueOf(rspMap.get("TradingDay"));
        String logintime = String.valueOf(rspMap.get("LoginTime"));
        String participantId = String.valueOf(rspMap.get("ParticipantID"));
        String ddlSessionId = String.valueOf(rspMap.get("SessionID"));//服务端的session
        userInfo = new UserInfo();
        userInfo.setUserID(userId);
        userInfo.setTradingDay(tradingday);
        userInfo.setLoginTime(logintime);
        userInfo.setParticipantID(participantId);
        userInfo.setDdlSessionID(ddlSessionId);
        userInfo.setSessionID(sessionId);
    }

    // 保存监听器引用
    private List<XiyueListener> listeners = Collections.synchronizedList(new ArrayList<>());
    // 注册监听器
    public void addListener(XiyueListener listener){
        listeners.add(listener);
    }
    public synchronized void updateRedis(Map<String, Object> fields){
        RedisEvent e = new RedisEvent(this);
        e.setFields(fields);
        fieldsChanged(e,null);
    }
    private void fieldsChanged(RedisEvent re , JPushEvent je){
        listeners.stream().forEach(listener -> {
            listener.handleRedisEvent(re);
            listener.handleJPushEvent(je);
        });
        listeners.clear();
    }

    @Override
    public ResultJson ReqUserLogin(String userId, String password) {
    	ResultJson rj;
    	int nRequestID;
    	lock();
    	try {
	    	CShfeFtdcReqUserLoginField reqUserLogin = new CShfeFtdcReqUserLoginField();
			// 发出登陆请求
			reqUserLogin.setUserID(userId);
			reqUserLogin.setPassword(password);
			reqUserLogin.setClientSideType(TraderApiConstants.SHFE_FTDC_CS_Ordinary);
			reqUserLogin.setClientIPAddress("");
			reqUserLogin.setClientSideVersion("");
			reqUserLogin.setDynamicPassword("");
			reqUserLogin.setMacAddress("");
			reqUserLogin.setParticipantID("");
			reqUserLogin.setProductVersion("");
			reqUserLogin.setProtocolInfo("");
			reqUserLogin.setTradingDay("");
			reqUserLogin.setPNext(null);
			//获取请求ID
			nRequestID = setSynLatch();
			traderApi.ReqUserLogin(reqUserLogin,nRequestID );
			reqUserLogin.delete();
    	} finally {
			unlock();
		}
		try {
            long start = System.currentTimeMillis();
            log.info("UserID=" + userId + " , jni ===> ReqUserLogin , nReuqestID=" + nRequestID + "，异步请求响应开始");
            //等待异步消息
            getSynLatchMap().get(nRequestID).await(60L ,TimeUnit.SECONDS);
            log.info("UserID=" + userId + " , jni ===> ReqUserLogin , nReuqestID=" + nRequestID + "，异步请求响应耗时 ==> " + (System.currentTimeMillis() - start)+"ms");
			//获取消息结果
			rj = getRequestMap().get(nRequestID);
			//获取结果完成，清除信息
			afterGetInfoAndDel(nRequestID);
		} catch (InterruptedException e) {
			rj = new ResultJson();
	    	rj.setIsSucc(false);
	    	rj.setMessage(e.getMessage());
			log.error("ReqUserLogin==>latch.await错误", e);
		}
		return rj;
    }
    @Override
	public ResultJson ReqQryTradingAccount() {
    	ResultJson rj ;
    	if(!Optional.ofNullable(getUserInfo()).isPresent()){
    		rj = new ResultJson();
    		rj.setIsSucc(false);
    		rj.setMessage("无法获取用户UserID=" + userId + ",请先登录！");
    		rj.setErrorCode(-1);
    		return rj;
    	}
    	try{
    		int nRequestID ; 
    		lock();
			try {
				CShfeFtdcQryTradingAccountField req = new CShfeFtdcQryTradingAccountField();
				// 发出获取资金帐号信息
				req.setAccountID("");
				req.setPartIDStart(userInfo.getParticipantID());
				req.setPartIDEnd(userInfo.getParticipantID());
				req.setPNext(null);
				//获取请求ID
				nRequestID = setSynLatch();
				traderApi.ReqQryTradingAccount(req, nRequestID);
				req.delete();
			}finally {
				unlock();
			}
			try {
                long start = System.currentTimeMillis();
                log.info("UserID=" + userId + " , jni ===> ReqQryTradingAccount , nReuqestID=" + nRequestID + "，异步请求响应开始");
                //等待异步消息
                getSynLatchMap().get(nRequestID).await(60L ,TimeUnit.SECONDS);
                log.info("UserID=" + userId + " , jni ===> ReqQryTradingAccount , nReuqestID=" + nRequestID + "，异步请求响应耗时 ==> " + (System.currentTimeMillis() - start)+"ms");
                //获取消息结果
				rj = getRequestMap().get(nRequestID);
				//获取结果完成，清除信息
				afterGetInfoAndDel(nRequestID);
			} catch (InterruptedException e) {
				rj = new ResultJson();
		    	rj.setIsSucc(false);
		    	rj.setMessage(e.getMessage());
				log.error("ReqQryTradingAccount==>latch.await错误", e);
			}
	    } catch (Exception ex) {
	    	rj = new ResultJson();
	    	rj.setIsSucc(false);
	    	rj.setMessage(ex.getMessage());
			log.error("ReqQryTradingAccount出现错误，UserID=" + userId, ex);
		}
		return rj;
	}

	@Override
	public ResultJson ReqUserPasswordUpdate(String oldPassword, String newPassword) {
    	ResultJson rj ;
    	try{
	    	int nRequestID ; 
	    	String message;
	    	if (StringUtils.isEmpty(userId) || StringUtils.isEmpty(oldPassword) || StringUtils.isEmpty(newPassword)) {
	    		rj = new ResultJson();
				message = "用户名或者密码为空!";
				log.info(message);
				rj.setIsSucc(false);
				rj.setMessage(message);
				return rj;
			}
	    	lock();
	    	try {
	    		CShfeFtdcUserPasswordUpdateField req = new CShfeFtdcUserPasswordUpdateField();
				req.setNewPassword(newPassword);
				req.setOldPassword(oldPassword);
				req.setPwdModifyType('0');// 0为自己修改 1为他人修改
				req.setUserID(userId);
				req.setPNext(null);
				//获取请求ID
				nRequestID = setSynLatch();
				traderApi.ReqUserPasswordUpdate(req, nRequestID);
				req.delete();
	    	} finally {
				unlock();
			}
			try {
                long start = System.currentTimeMillis();
                log.info("UserID=" + userId + " , jni ===> ReqUserPasswordUpdate , nReuqestID=" + nRequestID + "，异步请求响应开始");
                //等待异步消息
                getSynLatchMap().get(nRequestID).await(60L ,TimeUnit.SECONDS);
                log.info("UserID=" + userId + " , jni ===> ReqUserPasswordUpdate , nReuqestID=" + nRequestID + "，异步请求响应耗时 ==> " + (System.currentTimeMillis() - start)+"ms");
                //获取消息结果
				rj = getRequestMap().get(nRequestID);
				//获取结果完成，清除信息
				afterGetInfoAndDel(nRequestID);
			} catch (InterruptedException e) {
				rj = new ResultJson();
		    	rj.setIsSucc(false);
		    	rj.setMessage(e.getMessage());
				log.error("ReqUserPasswordUpdate==>latch.await错误", e);
			}
    	}catch (Exception ex) {
    		rj = new ResultJson();
	    	rj.setIsSucc(false);
	    	rj.setMessage(ex.getMessage());
			log.error("ReqUserPasswordUpdate出现错误，UserID=" + userId, ex);
		}
		return rj;
	}

	@Override
	public ResultJson ReqQryOrder( String startTime ) {
        ResultJson rj ;
        try{
            int nRequestID ;
            lock();
            try {
                CShfeFtdcQryOrderField req = new CShfeFtdcQryOrderField();
                req.setUserID(userId);
                req.setPartID(userInfo.getParticipantID());
                // 发出获取资金帐号信息
                if (!StringUtils.isEmpty(startTime)) {
                    req.setTimeStart(startTime);
                } else {
                    req.setTimeStart("");
                }
                req.setInstrumentID("");
                req.setTimeEnd("");
                req.setPNext(null);

                //获取请求ID
                nRequestID = setSynLatch();
                traderApi.ReqQryOrder(req, nRequestID);
                req.delete();
            }finally {
                unlock();
            }
            try {
                long start = System.currentTimeMillis();
                log.info("UserID=" + userId + " , jni ===> ReqQryOrder , nReuqestID=" + nRequestID + "，异步请求响应开始");
                //等待异步消息
                getSynLatchMap().get(nRequestID).await(60L ,TimeUnit.SECONDS);
                log.info("UserID=" + userId + " , jni ===> ReqQryOrder , nReuqestID=" + nRequestID + "，异步请求响应耗时 ==> " + (System.currentTimeMillis() - start)+"ms");
                //获取消息结果
                rj = getRequestMap().get(nRequestID);
                //获取结果完成，清除信息
                afterGetInfoAndDel(nRequestID);
            } catch (InterruptedException e) {
                rj = new ResultJson();
                rj.setIsSucc(false);
                rj.setMessage(e.getMessage());
                log.error("ReqQryOrder==>latch.await错误", e);
            }
        } catch (Exception ex) {
            rj = new ResultJson();
            rj.setIsSucc(false);
            rj.setMessage(ex.getMessage());
            log.error("ReqQryOrder，UserID=" + userId, ex);
        }
        return rj;
	}

	@Override
	public ResultJson ReqQryTrade( String startTime ) {
        ResultJson rj ;
        try{
            int nRequestID ;
            lock();
            try {
                CShfeFtdcQryTradeField req = new CShfeFtdcQryTradeField();
                req.setUserID(userId);
                req.setPartID(userInfo.getParticipantID());
                // 发出获取资金帐号信息
                if (!StringUtils.isEmpty(startTime)) {
                    req.setTimeStart(startTime);
                } else {
                    req.setTimeStart("");
                }
                char c = (char)0;
                req.setDirection(c);
                req.setInstrumentID("");
                req.setTimeEnd("");
                req.setPNext(null);

                //获取请求ID
                nRequestID = setSynLatch();
                traderApi.ReqQryTrade(req, nRequestID);
                req.delete();
            }finally {
                unlock();
            }
            try {
                long start = System.currentTimeMillis();
                log.info("UserID=" + userId + " , jni ===> ReqQryTrade , nReuqestID=" + nRequestID + "，异步请求响应开始");
                //等待异步消息
                getSynLatchMap().get(nRequestID).await(60L ,TimeUnit.SECONDS);
                log.info("UserID=" + userId + " , jni ===> ReqQryTrade , nReuqestID=" + nRequestID + "，异步请求响应耗时 ==> " + (System.currentTimeMillis() - start)+"ms");
                //获取消息结果
                rj = getRequestMap().get(nRequestID);
                //获取结果完成，清除信息
                afterGetInfoAndDel(nRequestID);
            } catch (InterruptedException e) {
                rj = new ResultJson();
                rj.setIsSucc(false);
                rj.setMessage(e.getMessage());
                log.error("ReqQryTrade==>latch.await错误", e);
            }
        } catch (Exception ex) {
            rj = new ResultJson();
            rj.setIsSucc(false);
            rj.setMessage(ex.getMessage());
            log.error("ReqQryTrade，UserID=" + userId, ex);
        }
        return rj;
	}

    @Override
    public ResultJson ReqOrderInsert(String instrumentID, double limitPrice, int volumeTotalOriginal, char direction, char offsetFlag, char marginType, String userId) {
        return null;
    }

	@Override
	public ResultJson ReqUserLogout() {
		return null;
	}

	@Override
	public ResultJson ReqQryPartDepositWithdraw() {
    	ResultJson rj ;
    	if(!Optional.ofNullable(getUserInfo()).isPresent()){
    		rj = new ResultJson();
    		rj.setIsSucc(false);
    		rj.setMessage("无法获取用户UserID=" + userId + ",请先登录！");
    		rj.setErrorCode(-1);
    		return rj;
    	}
    	try{
    		int nRequestID ; 
    		lock();
			try {
				CShfeFtdcQryPartDepositWithdrawField req = new CShfeFtdcQryPartDepositWithdrawField();
				req.setPartIDStart(userInfo.getParticipantID());
				req.setPartIDEnd(userInfo.getParticipantID());

				char c = (char)0;
				req.setProcessStatus(c);
				req.setStartDate("");
				req.setEndDate("");
				req.setTransDirection(c);
				req.setPNext(null);
				//获取请求ID
				nRequestID = setSynLatch();
				traderApi.ReqQryPartDepositWithdraw(req, nRequestID);
				req.delete();
			}finally {
				unlock();
			}
			try {
                long start = System.currentTimeMillis();
                log.info("UserID=" + userId + " , jni ===> ReqQryPartDepositWithdraw , nReuqestID=" + nRequestID + "，异步请求响应开始");
                //等待异步消息
                getSynLatchMap().get(nRequestID).await(60L ,TimeUnit.SECONDS);
                log.info("UserID=" + userId + " , jni ===> ReqQryPartDepositWithdraw , nReuqestID=" + nRequestID + "，异步请求响应耗时 ==> " + (System.currentTimeMillis() - start)+"ms");
				//获取消息结果
				rj = getRequestMap().get(nRequestID);
				//获取结果完成，清除信息
				afterGetInfoAndDel(nRequestID);
			} catch (InterruptedException e) {
				rj = new ResultJson();
		    	rj.setIsSucc(false);
		    	rj.setMessage(e.getMessage());
				log.error("ReqQryPartDepositWithdraw==>latch.await错误", e);
			}
	    } catch (Exception ex) {
	    	rj = new ResultJson();
	    	rj.setIsSucc(false);
	    	rj.setMessage(ex.getMessage());
			log.error("ReqQryPartDepositWithdraw出现错误，UserID=" + userId, ex);
		}
		return rj;
	}

	@Override
	public ResultJson ReqQryPartPosition() {
    	ResultJson rj ;
    	if(!Optional.ofNullable(getUserInfo()).isPresent()){
    		rj = new ResultJson();
    		rj.setIsSucc(false);
    		rj.setMessage("无法获取用户UserID=" + userId + ",请先登录！");
    		rj.setErrorCode(-1);
    		return rj;
    	}
    	try{
    		int nRequestID ;
    		lock();
			try {
				CShfeFtdcQryPartPositionField req = new CShfeFtdcQryPartPositionField();
				req.setPartIDStart(userInfo.getParticipantID());
				req.setPartIDEnd(userInfo.getParticipantID());
				req.setInstrumentID("");
				req.setPNext(null);

				//获取请求ID
				nRequestID = setSynLatch();
				traderApi.ReqQryPartPosition(req, nRequestID);
				req.delete();
			}finally {
				unlock();
			}
			try {
                long start = System.currentTimeMillis();
                log.info("UserID=" + userId + " , jni ===> ReqQryPartPosition , nReuqestID=" + nRequestID + "，异步请求响应开始");
                //等待异步消息
                getSynLatchMap().get(nRequestID).await(60L ,TimeUnit.SECONDS);
                log.info("UserID=" + userId + " , jni ===> ReqQryPartPosition , nReuqestID=" + nRequestID + "，异步请求响应耗时 ==> " + (System.currentTimeMillis() - start)+"ms");
				//获取消息结果
				rj = getRequestMap().get(nRequestID);
				//获取结果完成，清除信息
				afterGetInfoAndDel(nRequestID);
			} catch (InterruptedException e) {
				rj = new ResultJson();
		    	rj.setIsSucc(false);
		    	rj.setMessage(e.getMessage());
				log.error("ReqQryPartPosition==>latch.await错误", e);
			}
	    } catch (Exception ex) {
	    	rj = new ResultJson();
	    	rj.setIsSucc(false);
	    	rj.setMessage(ex.getMessage());
			log.error("ReqQryPartPosition出现错误，UserID=" + userId, ex);
		}
		return rj;
	}

	@Override
	public ResultJson ReqOrderAction(String orderSysID) {
		ResultJson rj;
    	if(!Optional.ofNullable(getUserInfo()).isPresent()){
    		rj = new ResultJson();
    		rj.setIsSucc(false);
    		rj.setMessage("无法获取用户UserID=" + userId + ",请先登录！");
    		rj.setErrorCode(-1);
    		return rj;
    	}
    	try{
    		int nRequestID ;
    		lock();
			try {
                CShfeFtdcOrderActionField req = new CShfeFtdcOrderActionField();
                req.setActionFlag('0');// 删除
                req.setOrderSysID(orderSysID);
                req.setParticipantID(userInfo.getParticipantID());
                req.setUserID(userId);
                req.setPNext(null);

				//获取请求ID
				nRequestID = setSynLatch();
				traderApi.ReqOrderAction(req, nRequestID);
				req.delete();
			}finally {
				unlock();
            }
			try {
                long start = System.currentTimeMillis();
                log.info("UserID=" + userId + " , jni ===> ReqOrderAction , nReuqestID=" + nRequestID + "，异步请求响应开始");
                //等待异步消息
                getSynLatchMap().get(nRequestID).await(60L ,TimeUnit.SECONDS);
                log.info("UserID=" + userId + " , jni ===> ReqOrderAction , nReuqestID=" + nRequestID + "，异步请求响应耗时 ==> " + (System.currentTimeMillis() - start)+"ms");
				//获取消息结果
				rj = getRequestMap().get(nRequestID);
				//获取结果完成，清除信息
				afterGetInfoAndDel(nRequestID);
				// 操作Redis数据库
                if(rj.getIsSucc()){
                    //注册监听器
                    this.addListener(new XiyueListener() {
                        @Override
                        public void handleRedisEvent(RedisEvent e) {
                            Map<String, Object> fields = e.getFields();
                            System.out.println(fields);
                        }
                        @Override
                        public void handleJPushEvent(JPushEvent e) {

                        }
                    });
                    Map<String , Object> attrs = new HashMap<>();
                    attrs.put("OrderStatus", "5");
                    //更新数据库
                    this.updateRedis(attrs);
                }
			} catch (InterruptedException e) {
				rj = new ResultJson();
		    	rj.setIsSucc(false);
		    	rj.setMessage(e.getMessage());
				log.error("ReqOrderAction==>latch.await错误", e);
			}
	    } catch (Exception ex) {
	    	rj = new ResultJson();
	    	rj.setIsSucc(false);
	    	rj.setMessage(ex.getMessage());
			log.error("ReqOrderAction出现错误，UserID=" + userId, ex);
		}
		return rj;
	}

	@Override
	public ResultJson ReqQryWarrantDetail() {
    	ResultJson rj;
    	if(!Optional.ofNullable(getUserInfo()).isPresent()){
    		rj = new ResultJson();
    		rj.setIsSucc(false);
    		rj.setMessage("无法获取用户UserID=" + userId + ",请先登录！");
    		rj.setErrorCode(-1);
    		return rj;
    	}
    	try{
    		int nRequestID ;
    		lock();
			try {
				CShfeFtdcReqQryWarrantDetailField req = new CShfeFtdcReqQryWarrantDetailField();
				req.setParticipantID(userInfo.getParticipantID());
				req.setCommodityID("");
				req.setWarValidity("");
				req.setPNext(null);

				//获取请求ID
				nRequestID = setSynLatch();
				traderApi.ReqQryWarrantDetail(req, nRequestID);
				req.delete();
			}finally {
				unlock();
			}
			try {
                long start = System.currentTimeMillis();
                log.info("UserID=" + userId + " , jni ===> ReqQryWarrantDetail , nReuqestID=" + nRequestID + "，异步请求响应开始");
                //等待异步消息
                getSynLatchMap().get(nRequestID).await(60L ,TimeUnit.SECONDS);
                log.info("UserID=" + userId + " , jni ===> ReqQryWarrantDetail , nReuqestID=" + nRequestID + "，异步请求响应耗时 ==> " + (System.currentTimeMillis() - start)+"ms");
				//获取消息结果
				rj = getRequestMap().get(nRequestID);
				//获取结果完成，清除信息
				afterGetInfoAndDel(nRequestID);
			} catch (InterruptedException e) {
				rj = new ResultJson();
		    	rj.setIsSucc(false);
		    	rj.setMessage(e.getMessage());
				log.error("ReqQryWarrantDetail==>latch.await错误", e);
			}
	    } catch (Exception ex) {
	    	rj = new ResultJson();
	    	rj.setIsSucc(false);
	    	rj.setMessage(ex.getMessage());
			log.error("ReqQryWarrantDetail出现错误，UserID=" + userId, ex);
		}
		return rj;
	}

    @Override
    public void OnRspUserLogin(CShfeFtdcRspUserLoginField pRspUserLogin, CShfeFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
    	Map<String, Object> succRspMap = null ;
       	Map<String, Object> errRspMap = null ;
    	lock();
    	try{
        	if (inexistErrorInfo(pRspInfo)) {
                if (pRspUserLogin != null) {
                    succRspMap = new  HashMap<>();
                    succRspMap.put("CheckUpdate", pRspUserLogin.getCheckUpdate());
                    succRspMap.put("CommPhaseNo", pRspUserLogin.getCommPhaseNo());
                    succRspMap.put("LoginTime", StringUtils.trim(pRspUserLogin.getLoginTime()));
                    succRspMap.put("MaxOrderLocalID", StringUtils.trim(pRspUserLogin.getMaxOrderLocalID()));
                    succRspMap.put("ParticipantID", StringUtils.trim(pRspUserLogin.getParticipantID()));
                    succRspMap.put("PrivateFlowSize", pRspUserLogin.getPrivateFlowSize());
                    succRspMap.put("SessionID", pRspUserLogin.getSessionID());
                    succRspMap.put("TradingDay", StringUtils.trim(pRspUserLogin.getTradingDay()));
                    succRspMap.put("TradingSystemName", StringUtils.trim(pRspUserLogin.getTradingSystemName()));
                    succRspMap.put("UpdateURL", StringUtils.trim(pRspUserLogin.getUpdateURL()));
                    succRspMap.put("serFlowSize", pRspUserLogin.getUserFlowSize());
                    succRspMap.put("UserID", StringUtils.trim(pRspUserLogin.getUserID()));
                    //存放登陆完成用户信息
                    setUserInfoModule(succRspMap, sessionId);
                }
        	}else{
                errRspMap = new HashMap<>();
        		errRspMap.put("ErrorID", pRspInfo.getErrorID());
        		errRspMap.put("ErrorMsg", pRspInfo.getErrorMsg());
        	}
    	}finally {
			unlock();
		}
    	handleRspInfo(nRequestID, succRspMap, errRspMap, bIsLast);
    	 if (bIsLast) {
    		 log.info("UserID=" + userId + " , jni ===> OnRspUserLogin , nReuqestID=" + nRequestID + "，异步请求响应结束");
    		 getSynLatchMap().get(nRequestID).countDown();
    	 }
    }
    @Override
	public void OnRspOrderAction(CShfeFtdcOrderActionField pOrderAction, CShfeFtdcRspInfoField pRspInfo, int nRequestID,
			boolean bIsLast) {
        Map<String, Object> succRspMap = null ;
        Map<String, Object> errRspMap = null ;
        lock();
        try{
            if (inexistErrorInfo(pRspInfo)) {
                if (pOrderAction != null) {
                    succRspMap = new  HashMap<>();
                    succRspMap.put("ActionFlag", pOrderAction.getActionFlag());
                    succRspMap.put("OrderSysID", StringUtils.trim(pOrderAction.getOrderSysID()));
                    succRspMap.put("ParticipantID", StringUtils.trim(pOrderAction.getParticipantID()));
                    succRspMap.put("UserID", StringUtils.trim(pOrderAction.getUserID()));
                    //存放登陆完成用户信息
                    setUserInfoModule(succRspMap, sessionId);
                }
            }else{
                errRspMap = new HashMap<>();
                errRspMap.put("ErrorID", pRspInfo.getErrorID());
                errRspMap.put("ErrorMsg", pRspInfo.getErrorMsg());
            }
        }finally {
            unlock();
        }
        handleRspInfo(nRequestID, succRspMap, errRspMap, bIsLast);
        if (bIsLast) {
            log.info("UserID=" + userId + " , jni ===> OnRspOrderAction , nReuqestID=" + nRequestID + "，异步请求响应结束");
            getSynLatchMap().get(nRequestID).countDown();
        }
	}

	@Override
	public void OnRspOrderInsert(CShfeFtdcInputOrderField pInputOrder, CShfeFtdcRspInfoField pRspInfo, int nRequestID,
			boolean bIsLast) {
		super.OnRspOrderInsert(pInputOrder, pRspInfo, nRequestID, bIsLast);
	}

	@Override
	public void OnRspQryOrder(CShfeFtdcOrderField pOrder, CShfeFtdcRspInfoField pRspInfo, int nRequestID,
			boolean bIsLast) {
        Map<String, Object> succRspMap = null ;
        Map<String, Object> errRspMap = null ;
        lock();
        try{
            if (inexistErrorInfo(pRspInfo)) {
                if (pOrder != null) {
                    succRspMap = new  HashMap<>();
                    succRspMap.put("ActiveUserID", StringUtils.trim(pOrder.getActiveUserID()));
                    succRspMap.put("CancelTime", StringUtils.trim(pOrder.getCancelTime()));
                    succRspMap.put("CancelType", pOrder.getCancelType());
                    succRspMap.put("CloseType", pOrder.getCloseType());
                    succRspMap.put("ContingentCondition", pOrder.getContingentCondition());
                    succRspMap.put("ContractID", StringUtils.trim(pOrder.getContractID()));
                    succRspMap.put("Direction", pOrder.getDirection());
                    succRspMap.put("ForceCloseReason", pOrder.getForceCloseReason());
                    succRspMap.put("InsertDate", StringUtils.trim(pOrder.getInsertDate()));
                    succRspMap.put("InsertTime", StringUtils.trim(pOrder.getInsertTime()));
                    succRspMap.put("InstrumentID", StringUtils.trim(pOrder.getInstrumentID()));
                    succRspMap.put("IsAutoSuspend", pOrder.getIsAutoSuspend());
                    succRspMap.put("LimitPrice", pOrder.getLimitPrice());
                    succRspMap.put("MarginType", pOrder.getMarginType());
                    succRspMap.put("MinVolume", pOrder.getMinVolume());
                    succRspMap.put("OffsetFlag", pOrder.getOffsetFlag());
                    succRspMap.put("OrderLocalID", StringUtils.trim(pOrder.getOrderLocalID()));
                    succRspMap.put("OrderPriceType", pOrder.getOrderPriceType());
                    succRspMap.put("OrderSource", pOrder.getOrderSource());
                    succRspMap.put("OrderStatus", pOrder.getOrderStatus());
                    succRspMap.put("OrderSysID", StringUtils.trim(pOrder.getOrderSysID()));
                    succRspMap.put("OrderType", pOrder.getOrderType());
                    succRspMap.put("ParticipantID", StringUtils.trim(pOrder.getParticipantID()));
                    succRspMap.put("Priority", pOrder.getPriority());
                    succRspMap.put("StopPrice", pOrder.getStopPrice());
                    succRspMap.put("TimeSortID", pOrder.getTimeSortID());
                    succRspMap.put("TradingDay", StringUtils.trim(pOrder.getTradingDay()));
                    succRspMap.put("UpdateTime", StringUtils.trim(pOrder.getUpdateTime()));
                    succRspMap.put("UserID", StringUtils.trim(pOrder.getUserID()));
                    succRspMap.put("VolumeCondition", pOrder.getVolumeCondition());
                    succRspMap.put("VolumeTotal", pOrder.getVolumeTotal());
                    succRspMap.put("VolumeTotalOriginal", pOrder.getVolumeTotalOriginal());
                    succRspMap.put("VolumeTraded", pOrder.getVolumeTraded());
                }
            }else{
                errRspMap = new  HashMap<>();
                errRspMap.put("ErrorID", pRspInfo.getErrorID());
                errRspMap.put("ErrorMsg", pRspInfo.getErrorMsg());
            }
        }finally {
            unlock();
        }
        handleRspInfo(nRequestID, succRspMap, errRspMap, bIsLast);
        if (bIsLast) {
            log.info("UserID=" + userId + " , jni ===> OnRspQryOrder , nReuqestID=" + nRequestID + "，异步请求响应结束");
            getSynLatchMap().get(nRequestID).countDown();
        }
	}

	@Override
	public void OnRspQryPartDepositWithdraw(CShfeFtdcRspPartDepositWithdrawField pRspPartDepositWithdraw,
			CShfeFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
    	Map<String, Object> succRspMap = null ;
       	Map<String, Object> errRspMap = null ;
    	lock();
    	try{
        	if (inexistErrorInfo(pRspInfo)) {
                if (pRspPartDepositWithdraw != null) {
                    succRspMap = new HashMap<>();
                    succRspMap.put("ExchgBankAccount", StringUtils.trim(pRspPartDepositWithdraw.getExchgBankAccount()));
                    succRspMap.put("ExchgBankName", StringUtils.trim(pRspPartDepositWithdraw.getExchgBankName()));
                    succRspMap.put("FundTransferType", pRspPartDepositWithdraw.getFundTransferType());
                    succRspMap.put("OperateDate", StringUtils.trim(pRspPartDepositWithdraw.getOperateDate()));
                    succRspMap.put("OperateNo", pRspPartDepositWithdraw.getOperateNo());
                    succRspMap.put("PartBankAccount", StringUtils.trim(pRspPartDepositWithdraw.getPartBankAccount()));
                    succRspMap.put("PartBankName", StringUtils.trim(pRspPartDepositWithdraw.getPartBankName()));
                    succRspMap.put("ParticipantID", StringUtils.trim(pRspPartDepositWithdraw.getParticipantID()));
                    succRspMap.put("ProcessStatus", pRspPartDepositWithdraw.getProcessStatus());
                    succRspMap.put("TransDirection", pRspPartDepositWithdraw.getTransDirection());
                    succRspMap.put("TransferSum", pRspPartDepositWithdraw.getTransferSum());
                    succRspMap.put("VersionNo", pRspPartDepositWithdraw.getVersionNo());
                }
        	}else{
        		 errRspMap = new  HashMap<>();
        		 errRspMap.put("ErrorID", pRspInfo.getErrorID());
        		 errRspMap.put("ErrorMsg", pRspInfo.getErrorMsg());
        	}
    	}finally {
			unlock();
		}
        handleRspInfo(nRequestID, succRspMap, errRspMap, bIsLast);
        if (bIsLast) {
            log.info("UserID=" + userId + " , jni ===> OnRspQryPartDepositWithdraw , nReuqestID=" + nRequestID + "，异步请求响应结束");
            getSynLatchMap().get(nRequestID).countDown();
        }
	}

	@Override
	public void OnRspQryPartPosition(CShfeFtdcRspPartPositionField pRspPartPosition, CShfeFtdcRspInfoField pRspInfo,
			int nRequestID, boolean bIsLast) {
    	Map<String, Object> succRspMap = null ;
       	Map<String, Object> errRspMap = null ;
    	lock();
    	try{
        	if (inexistErrorInfo(pRspInfo)) {
                if (pRspPartPosition != null) {
                    succRspMap = new  HashMap<>();
                    succRspMap.put("BuyAccountProLos", pRspPartPosition.getBuyAccountProLos());
                    succRspMap.put("BuyAccumCloseProfit", pRspPartPosition.getBuyAccumCloseProfit());
                    succRspMap.put("BuyPosiOrderFrozen", pRspPartPosition.getBuyPosiOrderFrozen());
                    succRspMap.put("BuyPosition", pRspPartPosition.getBuyPosition());
                    succRspMap.put("BuyRiskFrozenCashPosiVol", pRspPartPosition.getBuyRiskFrozenCashPosiVol());
                    succRspMap.put("BuyUseMargin", pRspPartPosition.getBuyUseMargin());
                    succRspMap.put("CanCloseBuyPosition", pRspPartPosition.getCanCloseBuyPosition());
                    succRspMap.put("CanCloseCashSellPosition", pRspPartPosition.getCanCloseCashSellPosition());
                    succRspMap.put("CanCloseSellPosition", pRspPartPosition.getCanCloseSellPosition());
                    succRspMap.put("CanCloseWarrantSellPosition", pRspPartPosition.getCanCloseWarrantSellPosition());
                    succRspMap.put("CashSellPosition", pRspPartPosition.getCashSellPosition());
                    succRspMap.put("InstrumentID", StringUtils.trim(pRspPartPosition.getInstrumentID()));
                    succRspMap.put("OpenAvePri", pRspPartPosition.getOpenAvePri());
                    succRspMap.put("ParticipantID", StringUtils.trim(pRspPartPosition.getParticipantID()));
                    succRspMap.put("SellAccountProLos", pRspPartPosition.getSellAccountProLos());
                    succRspMap.put("SellAccumCloseProfit", pRspPartPosition.getSellAccumCloseProfit());
                    succRspMap.put("SellAvePri", pRspPartPosition.getSellAvePri());
                    succRspMap.put("SellCashPosiOrderFrozen", pRspPartPosition.getSellCashPosiOrderFrozen());
                    succRspMap.put("SellPosition", pRspPartPosition.getSellPosition());
                    succRspMap.put("SellRiskFrozenCashPosiVol", pRspPartPosition.getSellRiskFrozenCashPosiVol());
                    succRspMap.put("SellRiskFrozenWarPosiVol", pRspPartPosition.getSellRiskFrozenWarPosiVol());
                    succRspMap.put("SellUseMargin", pRspPartPosition.getSellUseMargin());
                    succRspMap.put("SellWarrantPosiOrderFrozen", pRspPartPosition.getSellWarrantPosiOrderFrozen());
                    succRspMap.put("TradingDay", StringUtils.trim(pRspPartPosition.getTradingDay()));
                    succRspMap.put("WarrantSellPosition", pRspPartPosition.getWarrantSellPosition());
                }
        	}else{
        		 errRspMap = new  HashMap<>();
        		 errRspMap.put("ErrorID", pRspInfo.getErrorID());
        		 errRspMap.put("ErrorMsg", pRspInfo.getErrorMsg());
        	}
    	}finally {
			unlock();
		}
    	handleRspInfo(nRequestID, succRspMap, errRspMap, bIsLast);
        if (bIsLast) {
            log.info("UserID=" + userId + " , jni ===> OnRspQryPartPosition , nReuqestID=" + nRequestID + "，异步请求响应结束");
            getSynLatchMap().get(nRequestID).countDown();
        }
	}

	@Override
	public void OnRspQryTrade(CShfeFtdcTradeField pTrade, CShfeFtdcRspInfoField pRspInfo, int nRequestID,
			boolean bIsLast) {
        Map<String, Object> succRspMap = null ;
        Map<String, Object> errRspMap = null ;
        lock();
        try{
            if (inexistErrorInfo(pRspInfo)) {
                if (pTrade != null) {
                    succRspMap = new  HashMap<>();
                    succRspMap.put("AccountID", StringUtils.trim(pTrade.getAccountID()));
                    succRspMap.put("CloseContractID", StringUtils.trim(pTrade.getCloseContractID()));
                    succRspMap.put("CloseDateType", pTrade.getCloseDateType());
                    succRspMap.put("CloseType", pTrade.getCloseType());
                    succRspMap.put("DeriveSysID", StringUtils.trim(pTrade.getDeriveSysID()));
                    succRspMap.put("Direction", pTrade.getDirection());
                    succRspMap.put("InstrumentID", StringUtils.trim(pTrade.getInstrumentID()));
                    succRspMap.put("MarginType", pTrade.getMarginType());
                    succRspMap.put("OffsetFlag", pTrade.getOffsetFlag());
                    succRspMap.put("OrderSource", pTrade.getOrderSource());
                    succRspMap.put("OrderSysID", StringUtils.trim(pTrade.getOrderSysID()));
                    succRspMap.put("ParticipantID", StringUtils.trim(pTrade.getParticipantID()));
                    succRspMap.put("Price", pTrade.getPrice());
                    succRspMap.put("PriceSource", pTrade.getPriceSource());
                    succRspMap.put("TradeCloseProfit", pTrade.getTradeCloseProfit());
                    succRspMap.put("TradeID", StringUtils.trim(pTrade.getTradeID()));
                    succRspMap.put("TradeTime", StringUtils.trim(pTrade.getTradeTime()));
                    succRspMap.put("TradeType", pTrade.getTradeType());
                    succRspMap.put("TradingDay", StringUtils.trim(pTrade.getTradingDay()));
                    succRspMap.put("UserID", StringUtils.trim(pTrade.getUserID()));
                    succRspMap.put("Volume", pTrade.getVolume());
                }
            }else{
                errRspMap = new  HashMap<>();
                errRspMap.put("ErrorID", pRspInfo.getErrorID());
                errRspMap.put("ErrorMsg", pRspInfo.getErrorMsg());
            }
        }finally {
            unlock();
        }
        handleRspInfo(nRequestID, succRspMap, errRspMap, bIsLast);
        if (bIsLast) {
            log.info("UserID=" + userId + " , jni ===> OnRspQryTrade , nReuqestID=" + nRequestID + "，异步请求响应结束");
            getSynLatchMap().get(nRequestID).countDown();
        }
	}

	@Override
	public void OnRspQryTradingAccount(CShfeFtdcRspTradingAccountField pRspTradingAccount,
			CShfeFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
    	Map<String, Object> succRspMap = null ;
       	Map<String, Object> errRspMap = null ;
    	lock();
    	try{
        	if (inexistErrorInfo(pRspInfo)) {
                if (pRspTradingAccount != null) {
                    succRspMap = new HashMap<>();
                    succRspMap.put("AccountID", StringUtils.trim(pRspTradingAccount.getAccountID()));
                    succRspMap.put("AccountProLos", pRspTradingAccount.getAccountProLos());
                    succRspMap.put("ActualDelivReceiveCash", pRspTradingAccount.getActualDelivReceiveCash());
                    succRspMap.put("Available", pRspTradingAccount.getAvailable());
                    succRspMap.put("Balance", pRspTradingAccount.getBalance());
                    succRspMap.put("BaseReserve", pRspTradingAccount.getBaseReserve());
                    succRspMap.put("CloseProfit", pRspTradingAccount.getCloseProfit());
                    succRspMap.put("CurrMargin", pRspTradingAccount.getCurrMargin());
                    succRspMap.put("DelivCompenCash", pRspTradingAccount.getDelivCompenCash());
                    succRspMap.put("DelivFrozenCash", pRspTradingAccount.getDelivFrozenCash());
                    succRspMap.put("DelivMargin", pRspTradingAccount.getDelivMargin());
                    succRspMap.put("DelivProfit", pRspTradingAccount.getDelivProfit());
                    succRspMap.put("DelivReceiveCash", pRspTradingAccount.getDelivReceiveCash());
                    succRspMap.put("Deposit", pRspTradingAccount.getDeposit());
                    succRspMap.put("FrozenMargin", pRspTradingAccount.getFrozenMargin());
                    succRspMap.put("MonthTransFee", pRspTradingAccount.getMonthTransFee());
                    succRspMap.put("ParticipantID", StringUtils.trim(pRspTradingAccount.getParticipantID()));
                    succRspMap.put("PreBalance", pRspTradingAccount.getPreBalance());
                    succRspMap.put("RiskFrozenCash", pRspTradingAccount.getRiskFrozenCash());
                    succRspMap.put("TempAdjustCash", pRspTradingAccount.getTempAdjustCash());
                    succRspMap.put("TradingDay", StringUtils.trim(pRspTradingAccount.getTradingDay()));
                    succRspMap.put("TransFee", pRspTradingAccount.getTransFee());
                    succRspMap.put("WarrantPledgeCash", pRspTradingAccount.getWarrantPledgeCash());
                    succRspMap.put("Withdraw", pRspTradingAccount.getWithdraw());
                }
        	}else{
        		 errRspMap = new  HashMap<>();
        		 errRspMap.put("ErrorID", pRspInfo.getErrorID());
        		 errRspMap.put("ErrorMsg", pRspInfo.getErrorMsg());
        	}
    	}finally {
			unlock();
		}
    	handleRspInfo(nRequestID, succRspMap, errRspMap, bIsLast);
        if (bIsLast) {
            log.info("UserID=" + userId + " , jni ===> OnRspQryTradingAccount , nReuqestID=" + nRequestID + "，异步请求响应结束");
            getSynLatchMap().get(nRequestID).countDown();
        }
	}

	@Override
	public void OnRspQryWarrantDetail(CShfeFtdcRspQryWarrantDetailField pRspQryWarrantDetail,
			CShfeFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
    	Map<String, Object> succRspMap = null ;
       	Map<String, Object> errRspMap = null ;
    	lock();
    	try{
        	if (inexistErrorInfo(pRspInfo)) {
                if (pRspQryWarrantDetail != null) {
                    succRspMap = new  HashMap<>();
                    succRspMap.put("TradingDay", StringUtils.trim(pRspQryWarrantDetail.getTradingDay()));
                    succRspMap.put("ParticipantID", StringUtils.trim(pRspQryWarrantDetail.getParticipantID()));
                    succRspMap.put("CommodityID", StringUtils.trim(pRspQryWarrantDetail.getCommodityID()));
                    succRspMap.put("WarrantID", StringUtils.trim(pRspQryWarrantDetail.getWarrantID()));
                    succRspMap.put("WarVol", pRspQryWarrantDetail.getWarVol());
                    succRspMap.put("WarValidity", StringUtils.trim(pRspQryWarrantDetail.getWarValidity()));
                    succRspMap.put("RealWarVol", pRspQryWarrantDetail.getRealWarVol());
                    succRspMap.put("WarStatus", pRspQryWarrantDetail.getWarStatus());
                    succRspMap.put("WareHouseID", pRspQryWarrantDetail.getWareHouseID());
                    succRspMap.put("Premium", pRspQryWarrantDetail.getPremium());
                    succRspMap.put("InStorageID", StringUtils.trim(pRspQryWarrantDetail.getInStorageID()));
                    succRspMap.put("StoreorCarry", pRspQryWarrantDetail.getStoreorCarry());
                    succRspMap.put("RegisterType", pRspQryWarrantDetail.getRegisterType());
                    succRspMap.put("Remark", StringUtils.trim(pRspQryWarrantDetail.getRemark()));
                    succRspMap.put("AvaWarVol", pRspQryWarrantDetail.getAvaWarVol());
                    succRspMap.put("UseWarVol", pRspQryWarrantDetail.getUseWarVol());
                    succRspMap.put("RiskFrozenWarVol", pRspQryWarrantDetail.getRiskFrozenWarVol());
                    succRspMap.put("WarrantPledgeVol", pRspQryWarrantDetail.getWarrantPledgeVol());
                    succRspMap.put("DelivWarVol", pRspQryWarrantDetail.getDelivWarVol());
                    succRspMap.put("DelivFrozenWarVol", pRspQryWarrantDetail.getDelivFrozenWarVol());
                    succRspMap.put("ParticipantAbbr", StringUtils.trim(pRspQryWarrantDetail.getParticipantAbbr()));
                    succRspMap.put("WareHouseName", StringUtils.trim(pRspQryWarrantDetail.getWareHouseName()));
                    succRspMap.put("InputWarrantFrozenVol", pRspQryWarrantDetail.getInputWarrantFrozenVol());
                    succRspMap.put("InputWarrantUnFrozenVol", pRspQryWarrantDetail.getInputWarrantUnFrozenVol());
                    succRspMap.put("CommodityName", StringUtils.trim(pRspQryWarrantDetail.getCommodityName()));
                }
            }else{
                errRspMap = new  HashMap<>();
                errRspMap.put("ErrorID", pRspInfo.getErrorID());
                errRspMap.put("ErrorMsg", pRspInfo.getErrorMsg());
        	}
    	}finally {
			unlock();
		}
    	handleRspInfo(nRequestID, succRspMap, errRspMap, bIsLast);
        if (bIsLast) {
            log.info("UserID=" + userId + " , jni ===> OnRspQryWarrantDetail , nReuqestID=" + nRequestID + "，异步请求响应结束");
            getSynLatchMap().get(nRequestID).countDown();
        }
	}

	@Override
	public void OnRspUserPasswordUpdate(CShfeFtdcUserPasswordUpdateField pUserPasswordUpdate,
			CShfeFtdcRspInfoField pRspInfo, int nRequestID, boolean bIsLast) {
    	Map<String, Object> succRspMap = null ;
       	Map<String, Object> errRspMap = null ;
    	lock();
    	try{
        	if (inexistErrorInfo(pRspInfo)) {
                if (pUserPasswordUpdate != null) {
                    succRspMap = new  HashMap<>();
                    succRspMap.put("NewPassword", pUserPasswordUpdate.getNewPassword());
                    succRspMap.put("OldPassword", pUserPasswordUpdate.getOldPassword());
                    succRspMap.put("PwdModifyType", pUserPasswordUpdate.getPwdModifyType());
                    succRspMap.put("UserID", StringUtils.trim(pUserPasswordUpdate.getUserID()));
                }
        	}else{
                errRspMap = new  HashMap<>();
        		errRspMap.put("ErrorID", pRspInfo.getErrorID());
        		errRspMap.put("ErrorMsg", pRspInfo.getErrorMsg());
        	}
    	}finally {
			unlock();
		}
    	handleRspInfo(nRequestID, succRspMap, errRspMap, bIsLast);
        if (bIsLast) {
            log.info("UserID=" + userId + " , jni ===> OnRspUserPasswordUpdate , nReuqestID=" + nRequestID + "，异步请求响应结束");
            getSynLatchMap().get(nRequestID).countDown();
        }
	}
	@Override
	public void OnFrontConnected() {
		log.info("UserID=" + userId + ", 登陆响应 ===> OnFrontConnected ");
		isConnected = true;
	}
	
	/**
	 * 是否存在失败数据
	 */
	private boolean inexistErrorInfo(CShfeFtdcRspInfoField rspInfo) {
        return !(rspInfo != null && rspInfo.getErrorID() != 0);
    }

	//填加锁
	private void lock() {
		apiLock.lock();
	}

    //释放锁
	private void unlock() {
	    apiLock.unlock();
	}
	
}
