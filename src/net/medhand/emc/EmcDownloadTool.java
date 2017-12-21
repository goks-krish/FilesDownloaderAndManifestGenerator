package net.medhand.emc;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import com.google.gson.Gson;
import net.medhand.emc.model.Metadata;
import net.medhand.emc.model.Resource;
import net.medhand.emc.model.SPC;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;

public class EmcDownloadTool {
	static String baseURL = "http://api.uat05.medicines.org.uk/v1/";
	static String APIKEY = "17B9D964FDAA8145AC86A3AAD825F2CD";
	static String dst = "xml/xml/";
	static String imgDst = "xml/images/";
	static String outputXmlFile ="xml/demc.xml";
	static int offset = 1;
	private static final Logger LOGGER = Logger.getLogger(EmcDownloadTool.class.getName());	
	static FileHandler fh;  

	static TreeMap<String, String> titleAndId = new TreeMap<String , String>();
	static TreeMap<String, String> companyAndId = new TreeMap<String , String>();

	public static void main(String[] args) {
		try {
			DateFormat df = new SimpleDateFormat("ddMMyyyy_HHmmss");
			String reportDate = df.format(new Date());
			fh = new FileHandler("log/"+reportDate+".log"); 
			LOGGER.addHandler(fh);
	        SimpleFormatter formatter = new SimpleFormatter();  
	        fh.setFormatter(formatter);  
			
			LOGGER.info("Started: "+new Date());
			
			String fullRequestURL = baseURL + "documents?state=authorised&offset="+offset;
			
			/*1. Call API to read metadata & export*/
			System.out.println("\n\n\t\t### 1. Download XML");
			getMetatData(fullRequestURL);
			
			/*2. Downlaod Images from the xml files downloaded above*/
			System.out.println("\n\n\t\t### 2. Download Images");
			downloadImages(new File("xml/xml"));
			
			/*3. Create Manifest & Download Images*/
			System.out.println("\n\n\t\t### 3. Generate Manifest");
			generateManifest(new File("xml/xml"));
			
			
			LOGGER.info("\n\n****Completed: "+new Date());
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		
	}
	
	public static void getMetatData(String requestURL) {
		try {

			URL url = new URL(requestURL);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setRequestProperty("Accept", "application/json");
			conn.setRequestProperty("ApiKey", APIKEY);

			if (conn.getResponseCode() != 200) {
				LOGGER.warning(requestURL + " not reachable. -"+conn.getResponseCode());
				throw new RuntimeException("Failed : HTTP error code : "
						+ conn.getResponseCode());
			}

			BufferedReader br = new BufferedReader(new InputStreamReader(
				(conn.getInputStream())));

			String output="";
			String responseJson="";

			while ((output = br.readLine()) != null) {
				responseJson = responseJson + output;
			}
			
			Gson gson = new Gson();
			Metadata metadata = gson.fromJson(responseJson, Metadata.class);
			
			for(Resource resource:metadata.getResource()) {
				getPage(resource.getId()+"");
			}
			
			offset = offset + metadata.getLimit();
			
			conn.disconnect();
			br.close();
			
			if(metadata.getTotalRecords()>offset) {
				getMetatData(baseURL+"documents?state=authorised&offset="+offset);
			} else {
				System.out.println("Limit: "+ metadata.getLimit());
				System.out.println("Total Pages: " + metadata.getTotalPages());
				System.out.println("Total Records: " + metadata.getTotalRecords());
				System.out.println("OffSet: " + metadata.getOffset());
				System.out.println("Total Resources: " + metadata.getResource().size());
			}
			
		}
		catch (Exception e) {
			LOGGER.warning(" Something went wrong - "+e.getMessage());
			e.printStackTrace();
		}
		
	}
	
	public static void downloadImages(File path) {
		for(File file:path.listFiles()) {
			try {
				DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
				dbFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
				DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
				
				FileInputStream stream = new FileInputStream(file);
				Document doc = dBuilder.parse(stream);
				NodeList nodes = doc.getElementsByTagName("IMG");
				int size = nodes.getLength();
				for(int i=0;i<size;i++) {
					Element ele = (Element) nodes.item(i);
					getImage(ele.getAttribute("SRC"),file.getName());
				}
				
			} catch (Exception e) {
				LOGGER.warning(" Something went wrong in the file - " + file.getAbsolutePath() + " \n "+e.getMessage());
			}
		}
	}
	
	public static void getImage(String imgSrc, String fileName) {
		try {
			//./SPC.2175.12_FILES/IMAGE001.GIF ==> spc~2175~12~IMAGE001.GIF
			//1 Remove ./ 
			String imgURL= imgSrc.replace("./","");
		
			//2 Get image file & folder names
			String imageFolder = imgURL.split("/")[0];
			String imageName = imgURL.split("/")[1];
			
			//3 Construct http url
			imgURL = imageFolder.split("_FILES")[0].toLowerCase().replace(".", "~")+"~"+imageName;
			
			
			URL url = new URL(baseURL+"images/"+imgURL);
			
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setRequestProperty("Accept", "image/gif");
			conn.setRequestProperty("ApiKey", APIKEY);
			
			InputStream in = conn.getInputStream();
		    FileOutputStream out = new FileOutputStream(imgDst+ imageFolder + "_" +imageName);
		    
		    int c;
	        byte[] b = new byte[1024];
	        while ((c = in.read(b)) != -1) {
	            out.write(b, 0, c);
	        }
			
	        in.close();
	        out.flush();
	        out.close();
			conn.disconnect();
		}
		catch (Exception e) {
			LOGGER.warning(" Something went wrong - "+e.getMessage() + "\n File: "+fileName);
			e.printStackTrace();

		}
	}
	
	public static void getPage(String ID) {
		try {
			String requestURL = baseURL + "spcs/"+ID+"?format=xml";
			URL url = new URL(requestURL);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setRequestProperty("Accept", "application/json");
			conn.setRequestProperty("ApiKey", APIKEY);

			if (conn.getResponseCode() != 200) {
				throw new RuntimeException("Failed : HTTP error code : "
						+ conn.getResponseCode());
			}

			BufferedReader br = new BufferedReader(new InputStreamReader(
				(conn.getInputStream())));
			BufferedWriter writer = new BufferedWriter(new FileWriter(new File(dst+ID+".xml")));
			
			String output="";
			String responseJson="";

			while ((output = br.readLine()) != null) {
				responseJson = responseJson + output;
			}
			
			Gson gson = new Gson();
			SPC SPCData = gson.fromJson(responseJson, SPC.class);

			writer.write(SPCData.getResource().getContent());

			br.close();
			writer.flush();
			writer.close();
			conn.disconnect();
		}
		catch (Exception e) {
			LOGGER.warning(" Something went wrong - "+e.getMessage());
			e.printStackTrace();

		}
	}
	
	public static void generateManifest(File path) {
		for(File file:path.listFiles()) {
			readXML(file);
		}
		
		buildManifest();
	}
	
	public static void buildManifest() {
		DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder;	
        Document doc=null;
        try {
			docBuilder = dbfac.newDocumentBuilder();
	        doc = docBuilder.newDocument();
	        Element root = doc.createElement("manifest");
	        root.setAttribute("title", "eMC Medicines Information Services");
	        root.setAttribute("pullDate", new SimpleDateFormat("dd-MMM-yy").format(new Date()));
	        doc.appendChild(root);
	        
	        Element medicines = doc.createElement("medicines");
	        medicines.setAttribute("title", "MEDICINES");
	        Element companies = doc.createElement("companies");
	        companies.setAttribute("title", "COMPANIES");
	        root.appendChild(medicines);
	        root.appendChild(companies);
	        
	        String partVar = "";
	        Element alphaPart = null;
	        //MEDICINES
	        for (Map.Entry<String, String> entry : titleAndId.entrySet()) {
		        Element manifestFile = null;
				String title = entry.getKey().split(";")[0];
				String file = entry.getValue();
				
				String titleChar = title.substring(0, 1);
				char ch = titleChar.charAt(0);
				if(!(ch>='a' && ch<='z') && !(ch>='A' && ch<='Z')) {
					titleChar = "0-9";
				}
				
				if(!partVar.equalsIgnoreCase(titleChar)) {
					if(alphaPart!=null) {
						medicines.appendChild(alphaPart);
					}
					partVar = titleChar;
					alphaPart = doc.createElement("part");
					alphaPart.setAttribute("title", partVar.toUpperCase());
				}	
				
				manifestFile=doc.createElement("manifest-file");
				manifestFile.setAttribute("id", file.split(".xml")[0]);
				manifestFile.setAttribute("src", "xml\\"+file);
				manifestFile.setAttribute("title", title.trim());				
				alphaPart.appendChild(manifestFile);
	        }
	        if(alphaPart!=null) { //last set
				medicines.appendChild(alphaPart);
			}
	        alphaPart = null;
	        partVar = "";
	        Element company= null;
	        //COMPANIES
	        String lastCompanyName = "";
	        for (Map.Entry<String, String> entry : companyAndId.entrySet()) {
	        	try {
					String title = entry.getKey().split(";")[0];
					String file = entry.getKey().split(";")[1];
					String drugTitle = entry.getValue();
					
					String titleChar = title.substring(0, 1);
					char ch = titleChar.charAt(0);
					if(!(ch>='a' && ch<='z') && !(ch>='A' && ch<='Z')) {
						titleChar = "0-9";
					}
					
						
					
					Element drugName = doc.createElement("drug");
					drugName.setAttribute("title", drugTitle);
					drugName.setAttribute("id", file.split(".xml")[0]);
					
					if(!lastCompanyName.equalsIgnoreCase(title)) {
						if(company!=null){
							alphaPart.appendChild(company);	
						}
						lastCompanyName = title;
						company=doc.createElement("company");
						company.setAttribute("title", title.trim());				
					}
					company.appendChild(drugName);
					
					if(!partVar.equalsIgnoreCase(titleChar)) {
						if(alphaPart!=null) {
							companies.appendChild(alphaPart);
						}
						partVar = titleChar;
						alphaPart = doc.createElement("part");
						alphaPart.setAttribute("title", partVar.toUpperCase());
					}
	        	} catch (Exception e) {
	        		LOGGER.warning(" Something went wrong - "+e.getMessage());
	        	}
	        }
	        if(company!=null){
				alphaPart.appendChild(company);	
			}
	        if(alphaPart!=null) { //last set
	        	companies.appendChild(alphaPart);
			}
	        
	        buildManifestText(doc);
        } catch (Exception e) {
			LOGGER.warning(" Something went wrong - "+e.getMessage());
		}
	}
	
	public static void buildManifestText(Document doc){
		try{
	        TransformerFactory transfac = TransformerFactory.newInstance();
            Transformer trans = transfac.newTransformer();
            trans.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            trans.setOutputProperty(OutputKeys.INDENT, "yes");
            trans.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            //create string from xml tree
            StringWriter sw = new StringWriter();
            StreamResult result = new StreamResult(sw);
            DOMSource source = new DOMSource(doc);
            trans.transform(source, result);
            String xmlString = sw.toString();
            //print xml
            writeFile(xmlString);

		} catch(Exception e){
			LOGGER.severe(e.toString());
		}
		
	}
	
	public static void readXML(File fXmlFile){
		
		FileInputStream stream = null;
		try {
			
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			dbFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			
			stream = new FileInputStream(fXmlFile);
			Document doc = dBuilder.parse(stream);
			String title = doc.getElementsByTagName("TITLE").item(0).getTextContent();
			if(title != null && title.trim().length()!=0) {
				titleAndId.put(title.trim()+";"+fXmlFile.getName(), fXmlFile.getName());
			}
			
			NodeList nodeList = doc.getElementsByTagName("ORIGINAL");
			if(nodeList!=null) {
				String text = nodeList.item(0).getChildNodes().item(3).getTextContent();
				
				if(text==null || text.trim().length()==0){
					text = nodeList.item(0).getChildNodes().item(4).getTextContent();
				}
				if(text != null && text.trim().length()!=0) {
					companyAndId.put(text.trim()+";"+fXmlFile.getName(), doc.getElementsByTagName("TITLE").item(0).getTextContent());
				}
			}
			
		} catch (Exception e) {
			LOGGER.warning(" Something went wrong in the file - " + fXmlFile.getAbsolutePath() + " \n "+e.getMessage());
		}
	}
	
	public static void writeFile(String file){
		//System.out.println(file);
		try {
			BufferedWriter out = new BufferedWriter(new FileWriter(outputXmlFile));
			out.write(file);
			out.close();
			} catch (Exception e) { 
				e.printStackTrace(); 
				LOGGER.severe(e.toString());
				System.exit(-1);
				}	
	}
}
