package xiyue.simtrade.traderapi.redis.vo;

public class CRedisZSetField implements Comparable {
	public String getMember() {
		return member;
	}

	public void setMember(String member) {
		this.member = member;
	}

	public double getScore() {
		return score;
	}

	public void setScore(double score) {
		this.score = score;
	}

	// /member
	String member;
	// /score
	double score;

	@Override
	public int compareTo(Object o) {
		CRedisZSetField cz = (CRedisZSetField) o;
		double d = this.score - cz.score;
		if(d > 0){
			return 1;
		} else if(d < 0){
			return -1;
		} else {
			int a = this.member.hashCode() - cz.member.hashCode();
			return a;
		}
	}
	
	@Override
	public int hashCode(){
	   int l=  member.hashCode()+(int)score; //使用字符串哈希值与Integer的哈希值的组合
		                                      //这样会产生重码，实际上重码率很高
	   return l;
	}
	
	@Override
	public boolean equals(Object obj){
		CRedisZSetField p = (CRedisZSetField)obj;
		boolean iseql = member.equals(p.member) && score == p.score;
		return iseql;
	}
	
	@Override
	public String toString(){
		String str ="member:"+member+ ",score:"+score;
		return str;
	}

}
