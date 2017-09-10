import org.apache.http.Consts;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

public class UnsoldGoods {
    public static void main(String[] args) {
        Properties properties = new Properties();
        String username = null;
        String password = null;
        String worktype = null;
        String goodsName = null;
        boolean debug = true;
        int targetSaleVolume = 0;
        try {
            properties.load(new InputStreamReader(new FileInputStream("UnsoldConfig.properties"), "utf-8"));
            username = properties.getProperty("拼多多用户名");
            password = properties.getProperty("拼多多密码");
            worktype = properties.getProperty("想要的工作方式");
            goodsName = properties.getProperty("商品名称");
            debug = Boolean.parseBoolean(properties.getProperty("debug"));
            System.out.println(debug);
            targetSaleVolume = Integer.parseInt(properties.getProperty("最低销量"));
            System.out.println(targetSaleVolume);


            UserObject userObject = null;
            System.out.println("尝试使用 用户名 : " + username + "\t密码 : " + password + " 登录");
            if ((userObject = login(username, password, debug)) == null) {
                System.out.println("登录失败,请检查用户名密码");
                return;
            }
            System.out.println("登陆成功,准备进行工作");

            switch (worktype) {
                case "1"://删除所有销量低于targetSaleVolume的商品
                    if (deleteLowSaleGoods(userObject, targetSaleVolume) == false) {
                        System.out.println("出现问题请重试");
                    }
                    break;
                case "2":
                    if (deleteNamedGoods(userObject, goodsName) == false) {
                        System.out.println("出现问题请重试");
                    }
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static boolean deleteLowSaleGoods(UserObject userObject, int targetSaleVolume) {
        int pageSize = 15;
        HttpGet tempGet = new HttpGet("http://mms.pinduoduo.com/malls/" +
                userObject.getMallId() +
                "/goodsList?sort_by=id&sort_type=DESC&is_onsale=1&sold_out=0&size=" + pageSize + "&page=1");
        String content = null;
        try {
            HttpResponse res = userObject.getHttpClient().execute(tempGet);
            content = EntityUtils.toString(res.getEntity());

            JSONObject resultObject = new JSONObject(content);
            JSONObject dataObject = resultObject.getJSONObject("data");
            int totalGoods = dataObject.getInt("total");
            int totalPage = (totalGoods / pageSize) + ((totalGoods % pageSize == 0) ? 0 : 1);

            for (int i = 1; i <= totalPage; i++) {
                Thread.sleep(1000);
                System.out.println("正在处理第" + i + "页的数据,总共有" + totalPage + "页");
                String getUrl = "http://mms.pinduoduo.com/malls/" +
                        userObject.getMallId() +
                        "/goodsList?sort_by=id&sort_type=DESC&is_onsale=1&sold_out=0&size=" +
                        pageSize +
                        "&page=" +
                        i;
                if (userObject.isDebug() == true) {
                    System.out.println(getUrl);
                }

                HttpGet get = new HttpGet(getUrl);
                res = userObject.getHttpClient().execute(get);
                content = EntityUtils.toString(res.getEntity());
                if (userObject.isDebug() == true) {
                    System.out.println(content);
                }
                resultObject = new JSONObject(content);
                JSONArray goodsArray = resultObject.getJSONObject("data").getJSONArray("goodsList");

                for (int j = 0; j < goodsArray.length(); j++) {
                    JSONObject goodObject = (JSONObject) goodsArray.get(j);
                    int goodID = goodObject.getInt("id");
                    int soldQuantity = goodObject.getInt("sold_quantity");
                    boolean onSale = goodObject.getBoolean("is_onsale");
                    if (onSale) {
                        if (soldQuantity < targetSaleVolume) {
                            if (deleteOneGood(userObject, goodID) == false) {
                                System.out.println("下架商品 " + goodID + " 失败");
                            } else {
                                System.out.println("下架商品 " + goodID + " 成功");
                            }
                        }
                    }
                }
            }


        } catch (org.json.JSONException e) {
            System.out.println(content);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return true;
    }

    private static boolean deleteOneGood(UserObject userObject, int goodID) {
        String getUrl = "http://mms.pinduoduo.com/malls/" +
                userObject.getMallId() +
                "/goods/" +
                goodID +
                "/unonsale?goods_id=" +
                goodID;
        if (userObject.isDebug() == true) {
            System.out.println(getUrl);
        }
        HttpGet unsoldGet = new HttpGet(getUrl);

        try {
            HttpResponse res = userObject.getHttpClient().execute(unsoldGet);
            String content = EntityUtils.toString(res.getEntity());

            if (userObject.isDebug() == true) {
                System.out.println(content);
            }

            JSONObject resultObject = new JSONObject(content);

            if (resultObject.getBoolean("success")) {
                return true;
            }

        } catch (org.json.JSONException e) {
            return false;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return false;
    }

    private static boolean deleteNamedGoods(UserObject userObject, String goodsName) {

        return false;
    }

    private static UserObject login(String username, String password, boolean debug) {
        UserObject userObject = null;
        // 全局请求设置
        RequestConfig globalConfig = RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build();
        // 创建cookie store的本地实例
        CookieStore cookieStore = new BasicCookieStore();
        // 创建HttpClient上下文
        HttpClientContext context = HttpClientContext.create();
        context.setCookieStore(cookieStore);
        // 创建一个HttpClient
        CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(globalConfig)
                .setDefaultCookieStore(cookieStore).build();

        CloseableHttpResponse res = null;

        List<NameValuePair> valuePairs = new LinkedList<NameValuePair>();

        valuePairs.add(new BasicNameValuePair("username", username));
        valuePairs.add(new BasicNameValuePair("password", password));

        UrlEncodedFormEntity entity = new UrlEncodedFormEntity(valuePairs, Consts.UTF_8);
        entity.setContentType("application/x-www-form-urlencoded");

        // 创建一个post请求
        HttpPost post = new HttpPost("http://mms.pinduoduo.com/auth");
        // 注入post数据
        post.setEntity(entity);
        try {
            res = httpClient.execute(post, context);
            String content = EntityUtils.toString(res.getEntity());
            JSONObject resultObject = new JSONObject(content);
            if (resultObject.getString("username") == null) {//判定是否登陆成功
                return null;
            }
            userObject = new UserObject();
            userObject.setContext(context);
            userObject.setMallId(Integer.toString(resultObject.getInt("mall_id")));
            userObject.setHttpClient(httpClient);
            userObject.setDebug(debug);

            res.close();
        } catch (org.json.JSONException e) {
            return null;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return userObject;
    }

//    public void test() {
//
//
//        HttpGet httpGet = new HttpGet(
//                "http://mms.pinduoduo.com/malls/419829/goodsList?" +
//                        "sort_by=id&sort_type=DESC&goods_name=" +
//                        "%e9%ab%98%e8%80%83" +//这一行是要修改的名字
//                        "&size=15&page=1");
//        try {
//            res = httpClient.execute(httpGet, context);
//            String content = EntityUtils.toString(res.getEntity());
//            res.close();
//            //解析下架所有的商品
//
//            JSONObject jsonObject = new JSONObject(content);
//            JSONObject dataObject = jsonObject.getJSONObject("data");
//            JSONArray goodsList = dataObject.getJSONArray("goodsList");
//
//            for (int i = 0; i < goodsList.length(); i++) {
//                JSONObject goodInfo = (JSONObject) goodsList.get(i);
//                System.out.println(goodInfo.toString());
//
//                String idStr = Integer.toString((Integer) goodInfo.get("id"));
//                //已经解析到id了,下一步对每一个id都进行下架处理
//                HttpGet unsoldGet = new HttpGet("http://mms.pinduoduo.com/malls/" +
//                        "419829" +//这里需要注意一下,需要在前面解析出来店铺的id
//                        "/goods/" +
//                        idStr +
//                        "/unonsale?goods_id=" +
//                        idStr);
//
//                res = httpClient.execute(unsoldGet, context);
//                System.out.println(EntityUtils.toString(res.getEntity()));
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
//    }
}
