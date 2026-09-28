package cn.jhun.sanjiaohu;

public final class ElectricityOrdersPolicyTest {
    static int checks;
    static void check(boolean ok){checks++;if(!ok)throw new AssertionError("Order navigation check "+checks);}
    public static void main(String[] args){
        String base="https://h5cloud.17wanxiao.com:18443/CloudPayment/bill/";
        check(ElectricityOrdersPolicy.list(base+"record.do"));
        check(ElectricityOrdersPolicy.detail(base+"detail.do?orderID=fixture&payUserIdNum=test&hostCustomerCode=1862"));
        check(ElectricityOrdersPolicy.page(base+"record.do?test=1"));
        check(ElectricityOrdersPolicy.query(base+"queryOrderList.do"));
        check(!ElectricityOrdersPolicy.page(base+"queryOrderList.do"));
        check(ElectricityOrdersPolicy.home(base+"type.do"));
        for(String bad:new String[]{null,"",base+"selectPayProject.do",base+"pay.do",base+"type.do",base+"record.do/other",base+"../bill/record.do",
            "http://h5cloud.17wanxiao.com:18443/CloudPayment/bill/record.do",
            "https://h5cloud.17wanxiao.com/CloudPayment/bill/record.do",
            "https://h5cloud.17wanxiao.com.evil.test:18443/CloudPayment/bill/record.do",
            "https://user@h5cloud.17wanxiao.com:18443/CloudPayment/bill/record.do",
            "https://evil.test/CloudPayment/bill/record.do","javascript:history.back()","alipays://platformapi/startapp"})check(!ElectricityOrdersPolicy.page(bad));
        System.out.println("Read-only order navigation: "+checks+" checks passed");
    }
}
