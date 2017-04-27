package com.zrt.ptt.app.console.net;

import com.zrt.ptt.app.console.App;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Created by surpass on 2017-4-27.
 */

public class OkHttpKit {

    //	public static Map<String, String> myHeaders = new HashMap<>();
    //	public static List<Cookie> cookies;
    private static OkHttpClient client;
    //	public static HashMap<String, List<Cookie>> cookieStore = new HashMap<>();
    public static PersistentCookieStore cookieStore = new PersistentCookieStore(App.getInstance().getApplicationContext());

//	public static void setMyHeaders(Map<String, String> headers) {
//		OkHttpKit.myHeaders = headers;
//	}

    public static JSONObject connGet(String url, Map<String, String> params) throws JSONException, IOException {

        if (!NetUtils.isConnected(App.getInstance().getApplicationContext())) {
            throw new RuntimeException("没有网络连接，请重试！");
        }
        // 创建OkHttpClient对象
        if (client==null){
            createOkHttpClient();
        }

        if (params != null) {
            url += "?";
            int i = 1;
            int size = params.keySet().size();
            for (String key : params.keySet()) {
//				url += key + "=" + java.net.URLEncoder.encode(params.get(key), "utf8");
                url += key + "=" + params.get(key);
                if(i<size){
                    url += "&";
                }
                i++;
            }
        }

        URL mURL = new URL(url);

        Request.Builder requestBuilder = new Request.Builder()
                .url(mURL)
                .get();

        Request request = requestBuilder.build();

        Response response = client.newCall(request).execute();
        // 判断是否链接成功
        if (response.isSuccessful()) {
            String responseBody = response.body().string();
            response.body().close();
            return new JSONObject(responseBody);
        } else {
            response.body().close();
            throw new RuntimeException("错误码："
                    + response);
        }
    }

    public static JSONObject connPost(String url, Map<String, String> params) throws JSONException, IOException{

        if (!NetUtils.isConnected(App.getInstance().getApplicationContext())) {
            throw new RuntimeException("没有网络连接，请重试！");
        }
        // 创建OkHttpClient对象
        if (client==null){
            createOkHttpClient();
        }

        FormBody.Builder mBuilder = new FormBody.Builder();
        if (params != null) {
            for (String key : params.keySet()) {
                mBuilder.add(key, params.get(key)+"");
            }
        }
        RequestBody formBody = mBuilder.build();
        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .post(formBody);

//		if (myHeaders.size()!=0) {
//			for (String key : myHeaders.keySet()) {
//				requestBuilder.addHeader(key, myHeaders.get(key)+"");
//			}
////			Log.d("myHeaders", myHeaders.toString());
////			myHeaders.clear();
//		}

        Request request = requestBuilder.build();
        Response response = client.newCall(request).execute();
//		cookies = Cookie.parseAll(request.url(), response.headers());
        // 判断是否链接成功
        if (response.isSuccessful()) {
            String responseBody = response.body().string();
            response.body().close();
            return new JSONObject(responseBody);
        } else {
            response.body().close();
            throw new RuntimeException("错误码："
                    + response);
        }
    }

    private static void createOkHttpClient() {
        client = new OkHttpClient();
        client = client.newBuilder()
                .cookieJar(new CookieJar() {
                    @Override
                    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                        if (cookies != null && cookies.size() > 0) {
                            for (Cookie item : cookies) {
                                cookieStore.add(url, item);
                            }
                        }
                    }

                    @Override
                    public List<Cookie> loadForRequest(HttpUrl url) {
                        List<Cookie> cookies = cookieStore.get(url);
                        return cookies;

                    }
                })
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .connectTimeout(15, TimeUnit.SECONDS).build();
    }

}

