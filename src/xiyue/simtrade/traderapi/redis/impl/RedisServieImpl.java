package xiyue.simtrade.traderapi.redis.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.manyit.common.util.StringUtil;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisZSetCommands.Tuple;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;
import xiyue.simtrade.traderapi.redis.RedisServie;
import xiyue.simtrade.traderapi.redis.vo.CRedisZSetField;

@Service
public class RedisServieImpl implements RedisServie {


	 
	/**
	 * 获取 RedisSerializer <br>
	 * ------------------------------<br>
	 */
	protected RedisSerializer<String> getRedisSerializer(StringRedisTemplate redisTemplate) {
		return redisTemplate.getStringSerializer();
	}

	@Override
	public List<CRedisZSetField> ZRangeByScore(String key, double minscore,
			double maxsource,StringRedisTemplate redisTemplate) {
		List<CRedisZSetField> list = redisTemplate
				.execute(new RedisCallback<List<CRedisZSetField>>() {
					public List<CRedisZSetField> doInRedis(
							RedisConnection connection)
							throws DataAccessException {
						List<CRedisZSetField> list2 = new LinkedList<CRedisZSetField>();
						RedisSerializer<String> serializer = getRedisSerializer(redisTemplate);
						byte[] keyid = serializer.serialize(key);
						Set<Tuple> results = connection
								.zRangeByScoreWithScores(keyid, minscore,
										maxsource);
						for (Tuple tu : results) {
							CRedisZSetField item = new CRedisZSetField();
							item.setScore(tu.getScore());
							item.setMember(serializer.deserialize(tu.getValue()));
							list2.add(item);
						}
						return list2;
					}
				});
		return list;
	}

	@Override
	public CRedisZSetField ZLastRangeByScore(String key, double minscore,double maxscore,StringRedisTemplate redisTemplate) {
		CRedisZSetField rtn  = null;
		List<CRedisZSetField> list = ZRangeByScore(key,minscore,maxscore,redisTemplate);
		if(list != null && list.size() > 0){
			rtn  = list.get(list.size() -1);
		}
		return rtn;
	}
	// 根据source的值进行查找
	@Override
	public List<String> SUnion(List<String> keys,StringRedisTemplate redisTemplate) {
		List<String> list = redisTemplate
				.execute(new RedisCallback<List<String>>() {
					public List<String> doInRedis(RedisConnection connection)
							throws DataAccessException {
						List<String> list2 = new LinkedList<String>();
						RedisSerializer<String> serializer = getRedisSerializer(redisTemplate);
						List<byte[]> inputkeys = new ArrayList<byte[]>();
						for (String key : keys) {
							byte[] ikey = serializer.serialize(key);
							inputkeys.add(ikey);
						}
						byte[][] inputkeys2=new byte[inputkeys.size()][];
						for(int i=0;i<inputkeys.size();i++){
							inputkeys2[i]=inputkeys.get(i);
						}
						Set<byte[]> results = connection.sUnion(inputkeys2);
						for (byte[] it : results) {
							list2.add(serializer.deserialize(it));
						}
						return list2;
					}
				});
		return list;
	}

	@Override
	public Map<String, String> HGetAll(String key,StringRedisTemplate redisTemplate) {
		Map<String, String> map = redisTemplate.execute(new RedisCallback<Map<String, String>>() {
					public Map<String, String> doInRedis(
							RedisConnection connection)
							throws DataAccessException {
						Map<String, String> list2 = null;
						RedisSerializer<String> serializer = getRedisSerializer(redisTemplate);
						byte[] inputkey = serializer.serialize(key);
						Map<byte[], byte[]> map = connection.hGetAll(inputkey);
						for (Entry<byte[], byte[]> entry : map.entrySet()) {
							String itkey = serializer.deserialize(entry
									.getKey());
							String itval ;
							byte[] item = entry.getValue();
							if(item != null){
								  itval = byteToString(serializer,item);

							} else{
								itval = null;
							}
							if(list2 == null){
								list2 = new HashMap<String, String>();
							}
							list2.put(itkey, itval);
						}

						return list2;
					}
				});
		return map;
	}

	@Override
	public Map<String, String> HGetAllWithFilter(String key, StringRedisTemplate redisTemplate,
			List<String> keyfilters) {
		Map<String, String> map = redisTemplate.execute(new RedisCallback<Map<String, String>>() {
			public Map<String, String> doInRedis(
					RedisConnection connection)
					throws DataAccessException {
				Map<String, String> list2 = null;
				RedisSerializer<String> serializer = getRedisSerializer(redisTemplate);
				byte[] inputkey = serializer.serialize(key);
				Map<byte[], byte[]> map = connection.hGetAll(inputkey);
				for (Entry<byte[], byte[]> entry : map.entrySet()) {
					String itkey = serializer.deserialize(entry
							.getKey());
					if(!keyfilters.contains(itkey)) {
						continue;
					}
					String itval ;
					byte[] item = entry.getValue();
					if(item != null){
						  itval = byteToString(serializer,item);
						 
					} else{
						itval = null;
					}
					if(list2 == null){
						list2 = new HashMap<String, String>();
					}
					list2.put(itkey, itval);
				}

				return list2;
			}
		});
return map;
	}

	@Override
	public List<Map<String, String>> HGetAll(List<String> keys,StringRedisTemplate redisTemplate) {
		List<Map<String, String>> list = new LinkedList<Map<String, String>>();
		for (String key : keys) {
			Map<String,String> item = HGetAll(key,redisTemplate);
			if(item != null){
				list.add(item);
			}
		}
		return list;
	}

	
	@Override
	public List<String> ZRange(String key, long start, long end,StringRedisTemplate redisTemplate) {
		List<String> list = redisTemplate
				.execute(new RedisCallback<List<String>>() {
					public List<String> doInRedis(RedisConnection connection)
							throws DataAccessException {
						List<String> list2 = new LinkedList<String>();
						RedisSerializer<String> serializer = getRedisSerializer(redisTemplate);
						byte[] inputkey = serializer.serialize(key);
						Set<byte[]> result = connection.zRange(inputkey, start,
								end);
						for (byte[] it : result) {
							list2.add(serializer.deserialize(it));
						}

						return list2;
					}
				});
		return list;
	}

	@Override
	public String GetLastZRange(String key,StringRedisTemplate redisTemplate) {
		List<String> list = ZRange(key, -1, -1,redisTemplate);
		String val = null;
		if (list != null && list.size() > 0) {
			val = list.get(0);
		}
		return val;
	}

	@Override
	public List<String> HMGetOnlyValue(String key,List<String> fields,StringRedisTemplate redisTemplate) {
		List<String> list  = redisTemplate
				.execute(new RedisCallback<List<String>>() {
					public List<String> doInRedis(
							RedisConnection connection)
							throws DataAccessException {
						List<String> list2 = new  LinkedList<String>();
						RedisSerializer<String> serializer = getRedisSerializer(redisTemplate);
						byte[] inputkey = serializer.serialize(key);
						List<byte[]> inputkeys = new ArrayList<byte[]>();
						for (String field : fields) {
							byte[] ikey = serializer.serialize(field);
							inputkeys.add(ikey);
						}
						byte[][] inputkeys2=new byte[inputkeys.size()][];
						for(int i=0;i<inputkeys.size();i++){
							inputkeys2[i]=inputkeys.get(i);
						}
						List<byte[]>  listret = connection.hMGet(inputkey,inputkeys2);
						for(byte[] item:listret){
							if(item != null){
								 
								String itval = byteToString(serializer,item);
								list2.add(itval);
							} else{
								list2.add(null);
							}
							
						}
						return list2;
					}
				});
		boolean isallnull = true;
		for(int i=0;i<list.size();i++){
			String item = list.get(i);
			if(null != item){
				isallnull = false;
				break;
			}
		}
		if(isallnull){
			list = new LinkedList<String>();
		}
		return list;
	}
	
	@Override
	public List<Map<String,String>> HMGet(List<String> keys,List<String> fields,StringRedisTemplate redisTemplate) {
		List<Map<String,String>> list = new LinkedList<Map<String,String>>();
		for(String key:keys){
			List<String> vals = HMGetOnlyValue(key, fields,redisTemplate);
			if(vals.size() > 0){
				Map<String,String> item = new HashMap<String, String>();
				for(int i=0;i<vals.size();i++){
					item.put(fields.get(i), vals.get(i));
				}
				list.add(item);
			}
			
		}
		return list;
	}
	
	@Override
	public Map<String,String> HMGet(String key,List<String> fields,StringRedisTemplate redisTemplate) {
		 
	      List<String> vals = HMGetOnlyValue(key, fields,redisTemplate);
			Map<String,String> item = null;
			for(int i=0;i<vals.size();i++){
				if(item == null){
					item =  new HashMap<String, String>();
				}
				item.put(fields.get(i), vals.get(i));
			}
			 
		 
		return item;
	}
	
	@Override
	public String Get(String key,StringRedisTemplate redisTemplate) {
		String val = redisTemplate
				.execute(new RedisCallback<String>() {
					public String doInRedis(
							RedisConnection connection)
							throws DataAccessException {
						RedisSerializer<String> serializer = getRedisSerializer(redisTemplate);
						byte[] inputkey = serializer.serialize(key);
						String val = byteToString(serializer,connection.get(inputkey));
						return val;
					}
				});
		return val;
	}
	
	@Override
	public List<Map<String,String>> ZRangeByScoreAndGetHashDatas(String zkey,double minscore,double maxsource,String prehashkey,StringRedisTemplate redisTemplate) {
		List<Map<String,String>> datalist = new LinkedList<Map<String,String>>();
	    List<CRedisZSetField> sortlist =  ZRangeByScore(zkey, minscore, maxsource,redisTemplate);
		 int size2 = sortlist.size();
		 for(int i=0;i<size2;i++){
			 String key = prehashkey+sortlist.get(i).getMember();
			 Map<String,String> map = HGetAll(key, redisTemplate);
			 if(map != null){
				 datalist.add(map);
			 }
		 }
//			if(size2 > 0){
//				final RedisSerializer<String> serializer = redisTemplate.getStringSerializer();
//				final int size = size2;
//			    List<Object> list = redisTemplate.executePipelined(new RedisCallback<Map<String, String>>() {
//					@Override
//					public Map<String, String> doInRedis(
//							RedisConnection connection)
//							throws DataAccessException {
//						Map<String, String> mapitem = null;
//						RedisSerializer<String> serializer = getRedisSerializer(redisTemplate);
//						for(int i=0;i<size;i++){
//							String key = prehashkey+sortlist.get(i).getMember();
//							byte[] inputkey = serializer.serialize(key);
//							Map<byte[], byte[]> map = connection.hGetAll(inputkey);
//							if(map != null){
//								for (Map.Entry<byte[], byte[]> entry : map.entrySet()) {
//									String itkey = serializer.deserialize(entry.getKey());
//									String itval ;
//									byte[] item = entry.getValue();
//									if(item != null){
//									    String itval2 =  serializer.deserialize(item);
//									    itval = byteToString(itval2.getBytes());
//									} else{
//										itval = null;
//									}
//									if(mapitem == null){
//										mapitem = new HashMap<String, String>();
//									}
//									mapitem.put(itkey, itval);
//								}
//							}
//							
//						}
//						return mapitem;
//					}
//
//				}, serializer);
//			    if(list != null && list.size() > 0){
//			    	for(int i=0;i<list.size();i++){
//			    		Object item = list.get(i);
//			    		if(item != null){
//			    			Map<String,String> item2 = (Map<String,String>) item;
//			    			datalist.add(item2);
//			    		}
//			    		
//			    	}
//			    }
//			}
		return datalist;
	}
	
	@Override
	public List<Map<String,String>> ZRangeAndGetHashDatas(String zkey,long start,long end,String prehashkey,StringRedisTemplate redisTemplate) {
		List<Map<String,String>> datalist = new LinkedList<Map<String,String>>();
		List<String> sortlist =  ZRange(zkey, start, end,redisTemplate);
		int size2 = sortlist.size();
		for(int i=0;i<size2;i++){
			 String key = prehashkey + sortlist.get(i);
			 Map<String,String> map = HGetAll(key, redisTemplate);
			 if(map != null){
				 datalist.add(map);
			 }
			
		 }
//		if(size2 > 0){
//			final RedisSerializer<String> serializer = redisTemplate.getStringSerializer();
//			final int size = size2;
//		    List<Object> list = redisTemplate.executePipelined(new RedisCallback<Map<String, String>>() {
//				@Override
//				public Map<String, String> doInRedis(
//						RedisConnection connection)
//						throws DataAccessException {
//					Map<String, String> mapitem = null;
//					RedisSerializer<String> serializer = getRedisSerializer(redisTemplate);
//					for(int i=0;i<size;i++){
//						String key = sortlist.get(i);
//						byte[] inputkey = serializer.serialize(key);
//						Map<byte[], byte[]> map = connection.hGetAll(inputkey);
//						if(map != null){
//							for (Map.Entry<byte[], byte[]> entry : map.entrySet()) {
//								String itkey = serializer.deserialize(entry.getKey());
//								String itval ;
//								byte[] item = entry.getValue();
//								if(item != null){
//								    String itval2 =  serializer.deserialize(item);
//								    itval = byteToString(itval2.getBytes());
//								} else{
//									itval = null;
//								}
//								if(mapitem == null){
//									mapitem = new HashMap<String, String>();
//								}
//								mapitem.put(itkey, itval);
//							}
//						}
//					}
//					return mapitem;
//				}
//
//			}, serializer);
//		    if(list != null && list.size() > 0){
//		    	for(int i=0;i<list.size();i++){
//		    		Object item = list.get(i);
//		    		if(item != null){
//		    			Map<String,String> item2 = (Map<String,String>) item;
//		    			datalist.add(item2);
//		    		}
//		    	}
//		    }
//		}
		return datalist;
	}
	@Override
	public Map<String,String> ZLastRangeAndGetHashDatas(String zkey,String prehashkey,StringRedisTemplate redisTemplate) {
		Map<String,String> map = null;
		List<String> sortlist =  ZRange(zkey, -1, -1,redisTemplate);
		if(sortlist != null && sortlist.size() > 0){
			String seq = sortlist.get(sortlist.size() -1);
			String key = prehashkey+seq;
			map = HGetAll(key,redisTemplate);
		}
		return map;
	}
	
	@Override
	public List<String> SInter(List<String> keys,StringRedisTemplate redisTemplate) {
		List<String> list = redisTemplate
				.execute(new RedisCallback<List<String>>() {
					public List<String> doInRedis(RedisConnection connection)
							throws DataAccessException {
						List<String> list2 = new LinkedList<String>();
						RedisSerializer<String> serializer = getRedisSerializer(redisTemplate);
						List<byte[]> inputkeys = new ArrayList<byte[]>();
						for (String key : keys) {
							byte[] ikey = serializer.serialize(key);
							inputkeys.add(ikey);
						}
						byte[][] inputkeys2=new byte[inputkeys.size()][];
						for(int i=0;i<inputkeys.size();i++){
							inputkeys2[i]=inputkeys.get(i);
						}
						Set<byte[]> results = connection.sInter(inputkeys2);
						for (byte[] it : results) {
							list2.add(serializer.deserialize(it));
						}
						return list2;
					}
				});
		return list;
	}
	
	@Override
	public void HMSet(String key,Map<String,String> map,StringRedisTemplate redisTemplate) {
		redisTemplate.execute(new RedisCallback<Void>() {
					public Void doInRedis(RedisConnection connection)
							throws DataAccessException {
						
						
						RedisSerializer<String> serializer = getRedisSerializer(redisTemplate);
						byte[] inputkey = serializer.serialize(key);

                        Map<byte[],byte[]> inputmap = new HashMap<byte[], byte[]>();
                        for(Entry<String, String> item:map.entrySet()){
                        	String ikey = item.getKey();
                        	String ival  = item.getValue();
                        	byte[] iputkey = serializer.serialize(ikey);
                        	byte[] iputval = serializer.serialize(ival);
                        	inputmap.put(iputkey, iputval);
                        }
					    connection.hMSet(inputkey, inputmap);
					    return null;
					}
				});
	}
	@Override
	public void HMSet(List<Map<String,Map<String,String>>> datas,StringRedisTemplate redisTemplate) {
		if(datas.size() <= 0){
			return;
		}
			
		final RedisSerializer<String> serializer = redisTemplate.getStringSerializer();
		final int size = datas.size();
		redisTemplate.executePipelined(new RedisCallback<Void>() {
			@Override
			public Void doInRedis(RedisConnection connection) throws DataAccessException {
				for (int i = 0; i < size; i++) {
					for(Entry<String, Map<String,String>> item2:datas.get(i).entrySet()){
						String key = item2.getKey();
						Map<String,String> map = item2.getValue();
						byte[] inputkey = serializer.serialize(key);
	                    Map<byte[],byte[]> inputmap = new HashMap<byte[], byte[]>();
	                    for(Entry<String, String> item:map.entrySet()){
	                    	String ikey = item.getKey();
	                    	String ival  = item.getValue();
	                    	byte[] iputkey = serializer.serialize(ikey);
	                    	byte[] iputval = serializer.serialize(ival);
	                    	inputmap.put(iputkey, iputval);
	                    }
					    connection.hMSet(inputkey, inputmap);
					}
				}
				return null;
			}

		}, serializer);
	}
	
	
	@Override
	public void ZAdd(String key,List<CRedisZSetField> datas,StringRedisTemplate redisTemplate) {
		if(datas.size() <= 0){
			return;
		}
		final RedisSerializer<String> serializer = redisTemplate.getStringSerializer();
		final int size = datas.size();
		redisTemplate.executePipelined(new RedisCallback<Void>() {
			@Override
			public Void doInRedis(RedisConnection connection) throws DataAccessException {
				for (int i = 0; i < size; i++) {
					CRedisZSetField item = datas.get(i);
					byte[] inputkey = serializer.serialize(key);
                    byte[] inputmember = serializer.serialize(item.getMember());
                    double score = item.getScore();
                    connection.zAdd(inputkey, score, inputmember);
				}
				return null;
			}

		}, serializer);
			
	}
	
	@Override
	public boolean ZAdd(String key,double score,String member,StringRedisTemplate redisTemplate) {
		boolean issuc = redisTemplate.execute(new RedisCallback<Boolean>() {
					public Boolean doInRedis(RedisConnection connection)
							throws DataAccessException {
						RedisSerializer<String> serializer = getRedisSerializer(redisTemplate);
						byte[] inputkey = serializer.serialize(key);
                        byte[] inputmember = serializer.serialize(member);
                        Boolean issuc = connection.zAdd(inputkey, score, inputmember);
					   return issuc;
					}
				});
		return  issuc;
	}
	
//	@Override
//	public void ZAdd(String key,List<CRedisZSetField> members,StringRedisTemplate redisTemplate) {
//		for(int i=0;i<members.size();i++){
//			CRedisZSetField item = members.get(i);
//			double score = item.getScore();
//			String member = item.getMember();
//			ZAdd(key,score,member,redisTemplate);
//		}
//	}
	
	@Override
	public void Set(String key,String val,StringRedisTemplate redisTemplate) {
		redisTemplate.execute(new RedisCallback<Void>() {
					public Void doInRedis(RedisConnection connection)
							throws DataAccessException {
						RedisSerializer<String> serializer = getRedisSerializer(redisTemplate);
						byte[] inputkey = serializer.serialize(key);
						byte[] inputval = serializer.serialize(val);
					    connection.set(inputkey, inputval);
					    return null;
					}
		});
	}
	
	@Override
	public Long GetNextMaxId(String key,StringRedisTemplate redisTemplate){
		Long seq = redisTemplate.execute(new RedisCallback<Long>() {
			public Long doInRedis(RedisConnection connection)
					throws DataAccessException {
				RedisSerializer<String> serializer = getRedisSerializer(redisTemplate);
				byte[] inputkey = serializer.serialize(key);
				Long seq =  connection.incr(inputkey);
			    return seq;
			}
        });
		return seq;
	}
	
	@Override
	public Long Del(List<String> keys,StringRedisTemplate redisTemplate){
		Long cou = redisTemplate.execute(new RedisCallback<Long>() {
			public Long doInRedis(RedisConnection connection)
					throws DataAccessException {
				RedisSerializer<String> serializer = getRedisSerializer(redisTemplate);
				List<byte[]> inputkeys = new ArrayList<byte[]>();
				for (String key : keys) {
					byte[] ikey = serializer.serialize(key);
					inputkeys.add(ikey);
				}
				byte[][] inputkeys2=new byte[inputkeys.size()][];
				for(int i=0;i<inputkeys.size();i++){
					inputkeys2[i]=inputkeys.get(i);
				}
				Long cou =  connection.del(inputkeys2);
			    return cou;
			}
        });
		return cou;
	}
	@Override
	public Long Del(String key,StringRedisTemplate redisTemplate){
		List<String> keys = new LinkedList<String>();
		keys.add(key);
		Long cou= Del(keys,redisTemplate);
		return cou;
	}
	
	@Override
	public Long DelByLike(String prekey,StringRedisTemplate redisTemplate){
		if(StringUtil.isEmpty(prekey)){
			prekey ="*";
		}
		final String prekey2 = prekey;
		Long cou = redisTemplate.execute(new RedisCallback<Long>() {
			public Long doInRedis(RedisConnection connection)
					throws DataAccessException {
				RedisSerializer<String> serializer = getRedisSerializer(redisTemplate);
				byte[] inputkeys3 = serializer.serialize(prekey2);
				Set<byte[]> inputkeys = connection.keys(inputkeys3);
 				byte[][] inputkeys2=new byte[inputkeys.size()][];
 				int k = 0;
				for(byte[] item:inputkeys){
					inputkeys2[k]=item;
					k++;
				}
				Long cou = new Long(0);
				if(k > 0){
					cou =  connection.del(inputkeys2);
				}
			    return cou;
			}
        });
		return cou;
	}
	
	 
	@Override
	public Long ZCount(String key,double minscorce,double maxscore,StringRedisTemplate redisTemplate){
		Long cou = redisTemplate.execute(new RedisCallback<Long>() {
			public Long doInRedis(RedisConnection connection)
					throws DataAccessException {
				RedisSerializer<String> serializer = getRedisSerializer(redisTemplate);
				byte[] inputkeys = serializer.serialize(key);
			 
				Long cou =  connection.zCount(inputkeys, minscorce, maxscore);
			    return cou;
			}
        });
		return cou;
	}
	
	@Override
	public Long ZRem(String key,String member,StringRedisTemplate redisTemplate){
		Long cou = redisTemplate.execute(new RedisCallback<Long>() {
			public Long doInRedis(RedisConnection connection)
					throws DataAccessException {
				RedisSerializer<String> serializer = getRedisSerializer(redisTemplate);
				byte[] inputkey = serializer.serialize(key);
				byte[] inputmember = serializer.serialize(member);
				Long l =   connection.zRem(inputkey, inputmember);
			    return l;
			}
        });
		return cou;
	}
	
	@Override
	public Long ZRemRangeByScore(String key,double min,double max,StringRedisTemplate redisTemplate){
		Long cou = redisTemplate.execute(new RedisCallback<Long>() {
			public Long doInRedis(RedisConnection connection)
					throws DataAccessException {
				RedisSerializer<String> serializer = getRedisSerializer(redisTemplate);
				byte[] inputkey = serializer.serialize(key);
				Long l =   connection.zRemRangeByScore(inputkey, min, max);
			    return l;
			}
        });
		return cou;
	}
	private String byteToString(RedisSerializer<String> serializer,byte[] bytes){
		
		if(bytes == null){
			return null;
		}else{
//			  String itval2 =  serializer.deserialize(bytes);
			String str;
			try {
				String code = guessEncoding(bytes);
				str = new String(bytes,code);
			} catch (Exception e) {
				str = "";
			}
			return str;
		}
	}
	public String guessEncoding(byte[] bytes) throws IOException {  
	    String DEFAULT_ENCODING = "UTF-8";  
	    org.mozilla.universalchardet.UniversalDetector detector =  
	        new org.mozilla.universalchardet.UniversalDetector(null);  
	    detector.handleData(bytes, 0, bytes.length);  
	    detector.dataEnd();  
	    String encoding = detector.getDetectedCharset();  
	    detector.reset();  
	    if (encoding == null) {  
	        encoding = DEFAULT_ENCODING;  
	    }  
		//String encoding = getCharset(bytes);
	    return encoding;  
	}  
//	private String getCharset(byte[] bytes) throws IOException{
//		InputStream stream = new java.io.ByteArrayInputStream(bytes);
//		java.io.BufferedInputStream bin= new java.io.BufferedInputStream(stream);
//		String code;
//		int p =(bin.read()<<8)+bin.read();
//		switch(p){
//			case 0xefbb:{
//				code = "UTF-8";
//				break;
//			}
//			case 0xfffe:{
//				code = "Unicode";
//				break;
//			}
//			case 0xfeff:{
//				code = "UTF-16BE";
//				break;
//			}
//			default:{
//				code = "GBK";
//				break;
//			}
//		}
//		return code;
//	}
}
