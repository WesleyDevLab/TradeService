package xiyue.simtrade.traderapi;

import com.manyit.xytrade.traderapi.CSsecFtdcTraderSpi;
import xiyue.simtrade.traderapi.vo.ResultJson;


/**
 * Created by chenyan on 2017年5月19日 下午3:13:16 
 */
public abstract class TradeService extends  CSsecFtdcTraderSpi {
	
	// 用户登录请求
	public abstract ResultJson ReqUserLogin(String userId , String password);
	// 交易资金账户查询请求
	public abstract ResultJson ReqQryTradingAccount();
	// 用户密码修改请求
	public abstract ResultJson ReqUserPasswordUpdate(String oldPassword , String newPassword);
	// 报单查询查询请求
	public abstract ResultJson ReqQryOrder(String startTime);
	// 成交查询查询请求
	public abstract ResultJson ReqQryTrade(String startTime);
	// 报单录入请求
	public abstract ResultJson ReqOrderInsert(String instrumentID, double limitPrice, int volumeTotalOriginal, char direction, char offsetFlag, char marginType, String userId);
	// 用户登出请求
	public abstract ResultJson ReqUserLogout();
	// 交易商出入金查询请求
	public abstract ResultJson ReqQryPartDepositWithdraw();
	// 当日交易商合同持仓查询
	public abstract ResultJson ReqQryPartPosition();
	// 报单操作请求
	public abstract ResultJson ReqOrderAction(String orderSysID);
	// 仓单明细查询
	public abstract ResultJson ReqQryWarrantDetail();
}
