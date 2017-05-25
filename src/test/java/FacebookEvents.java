import org.testng.annotations.Test;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.regex.Pattern;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import net.lightbody.bmp.BrowserMobProxy;
import net.lightbody.bmp.BrowserMobProxyServer;
import net.lightbody.bmp.client.ClientUtil;
import net.lightbody.bmp.filters.RequestFilter;
import net.lightbody.bmp.filters.RequestFilterAdapter;
import net.lightbody.bmp.filters.ResponseFilter;
import net.lightbody.bmp.filters.ResponseFilterAdapter;
import net.lightbody.bmp.util.HttpMessageContents;
import net.lightbody.bmp.util.HttpMessageInfo;
import org.json.*;

public class FacebookEvents {
	
	private int eventResponseCount;

	String driverPath = "/Users/edwards/chromedriver";

	private WebDriver driver;
	private BrowserMobProxy proxy;
	private ArrayList<String> eventResponseJson;
	
	private static final String FB_USER = "YOUR FB EMAIL";
	private static final String FB_PW = "YOUR FB PW";
	private static final int PAGES_TO_SCROLL = 200;

	@BeforeTest
	public void setUp() throws InterruptedException {
		
		eventResponseCount = 0;	
		eventResponseJson = new ArrayList<String>();

		// start the proxy
		proxy = new BrowserMobProxyServer();
		proxy.start(9109);
	
		// get the Selenium proxy object - org.openqa.selenium.Proxy;
		Proxy seleniumProxy = ClientUtil.createSeleniumProxy(proxy);

		// configure it as a desired capability
		DesiredCapabilities capabilities = new DesiredCapabilities();
		capabilities.setCapability(CapabilityType.PROXY, seleniumProxy);

		// set chromedriver system property
		System.setProperty("webdriver.chrome.driver", driverPath);
		driver = new ChromeDriver(capabilities);	
		
		// Use gzip compression for all requests since FB's default is brotli which is incompatible with BrowserMob
		proxy.addFirstHttpFilterFactory(new RequestFilterAdapter.FilterSource(new RequestFilter() {
            @Override
            public HttpResponse filterRequest(HttpRequest request, HttpMessageContents contents, HttpMessageInfo messageInfo) {
                       
            	request.headers().set(HttpHeaders.Names.ACCEPT_ENCODING, HttpHeaders.Values.GZIP);
                return null;
            }
        },16777216));
	
		// Capture the responses that contain the JSON event data
		proxy.addFirstHttpFilterFactory(new ResponseFilterAdapter.FilterSource(new ResponseFilter() {
			@Override
			public void filterResponse(HttpResponse response, HttpMessageContents contents,
					HttpMessageInfo messageInfo) {

				String responseContentType = response.headers().get(HttpHeaders.Names.CONTENT_TYPE);

				if (responseContentType != null && responseContentType.equals("application/x-javascript; charset=utf-8")
						&& (messageInfo.getOriginalUrl().endsWith("?dpr=1"))) {
					
					// Remove the 'for(;;);' appended to the JSON response
					String eventData = contents.getTextContents().replaceFirst(Pattern.quote("for (;;);"), "");
					
					System.out.println(++eventResponseCount + " " + eventData);
					eventResponseJson.add(eventData);				
				}
			}
		},16777216));

		
		// Log in and go to the discover events page
		driver.get("http://facebook.com");
		driver.findElement(By.id("email")).sendKeys(FB_USER);
		driver.findElement(By.id("pass")).sendKeys(FB_PW);
		driver.findElement(By.id("loginbutton")).submit();	
		driver.get("http://facebook.com/events/discovery");
		
		Thread.sleep(5000);
		
		// Infinitely scroll
		for(int i = 0; i<PAGES_TO_SCROLL; i++){
			((JavascriptExecutor)driver).executeScript("window.scrollTo(0, document.body.scrollHeight);");		
			Thread.sleep(2000);
			System.out.println("Loading page" + i);
		}
	}

	@Test
	public void testCaseOne() {

	}

	@AfterTest
	public void tearDown() {
			
		Iterator<String> responsesIterator = eventResponseJson.iterator();
		while (responsesIterator.hasNext()) {
			
			JSONObject obj = new JSONObject(responsesIterator.next());
		
			try{
			JSONArray events = obj.getJSONObject("payload").getJSONArray("results").getJSONObject(0).getJSONArray("events");
			
			for (int i = 0; i < events.length(); i++)
			{
			    String title = events.getJSONObject(i).getString("title");
			    String dateAndTime = events.getJSONObject(i).getString("dateAndTime");
			    String socialContext = events.getJSONObject(i).getString("socialContext");
			    
			    if(socialContext.contains("interested")){
			    	socialContext = "";
			    }
			    
			    System.out.println(title + " " + dateAndTime + " " + socialContext);
			   
			}
			}catch(JSONException ex){
				
			}
		}
		
		if (driver != null) {
			proxy.stop();
			driver.quit();
		}
	}
}