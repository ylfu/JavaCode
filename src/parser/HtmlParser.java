package parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

class WebInfo {
	public String wikiPage; // its Wiki page.
	public String name;
	public String homePage; // its home page
	public String title;
	public String rank; // its rank in Alexa
}

class WebInfoComparator implements Comparator<WebInfo>{
	public int compare(WebInfo web1, WebInfo web2){
		return web1.name.compareTo(web2.name);
	}
}

public class HtmlParser {
	public static ArrayList<WebInfo> webinfos = new ArrayList<WebInfo>();

	// parse url, then write results to file "filename".
	public static void doParse(String url, String filename){
		parseUrls(url);
		dealWithEachUrl();
		writeHtmlFile(filename);
	}
	
	// parseUrls from URL "url".
	public static void parseUrls(String url) {
		String baseURL = "http://en.wikipedia.org";
		
		try {
			Document doc = Jsoup.connect(url).get();

			// Extract web information from tables
			Elements tables = doc.select("table");
			int count = 0;
			for (Element table : tables) {
				count++;
				if (count >= 2) {
					break;
				}
				Elements trs = table.select("tr");
				for (Element tr : trs) {
					// Only first `td` is website.
					Element td = tr.select("td").first();
					if(td == null)
						continue;
					
					Element as = td.select("a").first();
					if(as == null)
						continue;
					
					WebInfo tmpinfo = new WebInfo();
					tmpinfo.wikiPage = baseURL + as.attr("href");
					tmpinfo.name = as.text();
					webinfos.add(tmpinfo);
					
					System.out.println(tmpinfo.name + "  " + tmpinfo.wikiPage);
				}
			}
			
			System.out.println("Total " + webinfos.size() + " urls.");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void dealWithEachUrl(){
		for(WebInfo webinfo : webinfos) {
			System.out.println("Processing web : " + webinfo.name + " ...");
			
			String home_page = getHomePage(webinfo.wikiPage);
			System.out.println(home_page);
			webinfo.homePage = home_page;
			webinfo.title = getTitle(home_page);
			System.out.println(webinfo.title);
			webinfo.rank = getRank(home_page);
			System.out.println(webinfo.rank);
		}
	}
	
	public static String getHomePage(String wiki_page) {
		//System.out.println("Get home page from " + wiki_page);
		String content = new String();
		try {
			content = getWebContent(wiki_page);
		}catch (Exception e) {
			e.printStackTrace();
			return "NotExist";
		}
		
		String regEx = "<a rel=\"nofollow\" class=\"external text\" href=\"([^>]*)\">";
		Pattern pat = Pattern.compile(regEx);
		Matcher mat = null;
		
		int index = content.indexOf("<span class=\"mw-headline\" id=\"External_links\">External links</span>");

		if (index != -1) {
			mat = pat.matcher(content.substring(index));
		} else {
			mat = pat.matcher(content);
		}

		if (mat.find()) {
			return mat.group(1);
		} else {
			return "NotExist";
		}
	}

	public static String getTitle(String url) {
		String content = "";
		String title = "No Title";
		
		try {
			content = getWebContent(url);
		} catch (Exception e) {
			e.printStackTrace();
			if (content.equals("")) {
				return title;
			}
		}
		
		String regEx = "<title([^>]*)>(.*)</title>";
		Pattern pat = Pattern.compile(regEx);
		Matcher mat = pat.matcher(content);
		
		if (mat.find()) {
			title = mat.group(2);
		}
		
		return title;
	}

	public static String getRank(String url) {
		String content = "";
		String rank = "No Rank";
		
		url = "http://www.alexa.com/siteinfo/" + url;
		
		try {
			content = getWebContent(url);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			String regEx = "<strong class=\"metricsUrl font-big2 valign\"><a href=\"[^>]*\">([0-9,]*)</a></strong>";
			Pattern pat = Pattern.compile(regEx);
			Matcher mat = null;
			mat = pat.matcher(content);
			if (mat.find()) {
				rank = mat.group(1);
			}
		}
		
		return rank;
	}

	public static String getWebContent(String url) throws Exception {
		// System.out.println("Geting content from url : " + url);
		HttpClient client = new HttpClient();
		GetMethod method = new GetMethod(url);
		method.getParams().setParameter(HttpMethodParams.RETRY_HANDLER,
				new DefaultHttpMethodRetryHandler());
		//method.getParams().setParameter("http.protocol.cookie-policy",
		//		CookiePolicy.BROWSER_COMPATIBILITY);
		
		int statusCode = client.executeMethod(method);
		if (statusCode != HttpStatus.SC_OK) {
			System.err.println("Method failed: " + method.getStatusLine());
			return "";
		}
		
		InputStream resStream = method.getResponseBodyAsStream();
		BufferedReader br = new BufferedReader(new InputStreamReader(resStream));
		StringBuffer resBuffer = new StringBuffer();
		String resTemp = "";
		while ((resTemp = br.readLine()) != null) {
			resBuffer.append(resTemp);
		}
		String response = resBuffer.toString();
		return response;
	}

	public static void writeHtmlFile(String fileName) {
		File file = new File(fileName);
		Collections.sort(webinfos, new WebInfoComparator());
		try {
			PrintWriter writer = new PrintWriter(new FileWriter(file));
			writePrelude(writer);
			for(WebInfo webinfo : webinfos) {
				writeWebInfo(writer, webinfo);
			}
			writePostlude(writer);
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void writePrelude(PrintWriter writer) {
		writer.print("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">");
		writer.print("<html xmlns=\"http://www.w3.org/1999/xhtml\">");
		writer.print("<head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" /><title>Website Information</title></head>");
		writer.print("<body>");
		writer.print("<table width=\"550\" border=\"1\">");
		writer.print("<tr><td width=\"200\">网站名称</td><td width=\"300\">网站首页标题</td><td width=\"100\">Alexa排名</td></tr>");
	}
	
	public static void writeWebInfo(PrintWriter writer, WebInfo webinfo) {
		writer.print("<tr>");
		writer.print("<td>");
		writer.print("<a href='" + webinfo.homePage + "'>" + webinfo.name + "</a>");
		writer.print("</td>");
		writer.print("<td>" + webinfo.title + "</td>");
		writer.print("<td>" + webinfo.rank + "</td>");
		writer.print("</tr>");
	}
	
	public static void writePostlude(PrintWriter writer) {
		writer.print("</table></body></html>");
	}

	public static void main(String[] args) {
		doParse("http://en.wikipedia.org/wiki/List_of_search_engines", "./results.html");
	}
}