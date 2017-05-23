package xiyue.simtrade.traderapi.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import xiyue.simtrade.traderapi.redis.vo.CRedisZSetField;

import java.util.List;
import java.util.Map;

 

public interface RedisServie {

	List<CRedisZSetField> ZRangeByScore(String key, double minscore,
										double maxsource, StringRedisTemplate redisTemplate);

	CRedisZSetField ZLastRangeByScore(String key, double minscore,
                                      double maxscore, StringRedisTemplate redisTemplate);

	List<String> SUnion(List<String> keys,
                        StringRedisTemplate redisTemplate);

	List<Map<String, String>> HGetAll(List<String> keys,
                                      StringRedisTemplate redisTemplate);

	Map<String, String> HGetAll(String key,
                                StringRedisTemplate redisTemplate);
	
	Map<String, String> HGetAllWithFilter(String key,
                                          StringRedisTemplate redisTemplate, List<String> keyfilters);

	List<String> ZRange(String key, long start, long end,
                        StringRedisTemplate redisTemplate);

	String GetLastZRange(String key, StringRedisTemplate redisTemplate);

	List<String> HMGetOnlyValue(String key, List<String> fields,
                                StringRedisTemplate redisTemplate);

	Map<String, String> HMGet(String key, List<String> fields,
                              StringRedisTemplate redisTemplate);

	List<Map<String, String>> HMGet(List<String> keys, List<String> fields,
                                    StringRedisTemplate redisTemplate);

	List<Map<String, String>> ZRangeAndGetHashDatas(String zkey, long start,
                                                    long end, String prehashkey,
                                                    StringRedisTemplate redisTemplate);

	List<Map<String, String>> ZRangeByScoreAndGetHashDatas(String zkey,
                                                           double minscore, double maxsource, String prehashkey,
                                                           StringRedisTemplate redisTemplate);

	Map<String, String> ZLastRangeAndGetHashDatas(String zkey,
                                                  String prehashkey, StringRedisTemplate redisTemplate);

	List<String> SInter(List<String> keys,
                        StringRedisTemplate redisTemplate);

	void HMSet(String key, Map<String, String> map,
               StringRedisTemplate redisTemplate);

	void ZAdd(String key, List<CRedisZSetField> members,
              StringRedisTemplate redisTemplate);

	boolean ZAdd(String key, double score, String member,
                 StringRedisTemplate redisTemplate);

	Long GetNextMaxId(String key, StringRedisTemplate redisTemplate);

	void Set(String key, String val, StringRedisTemplate redisTemplate);

	Long Del(String key, StringRedisTemplate redisTemplate);

	Long Del(List<String> keys, StringRedisTemplate redisTemplate);

	Long DelByLike(String prekey, StringRedisTemplate redisTemplate);

	Long ZCount(String key, double minscorce, double maxscore,
                StringRedisTemplate redisTemplate);

	Long ZRem(String key, String member,
              StringRedisTemplate redisTemplate);


	void HMSet(List<Map<String, Map<String, String>>> datas,
               StringRedisTemplate redisTemplate);

	String Get(String key, StringRedisTemplate redisTemplate);

	Long ZRemRangeByScore(String key, double min, double max,
                          StringRedisTemplate redisTemplate);

}
