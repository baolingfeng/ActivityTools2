package activity.web.postproc;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.sf.json.JSONObject;

import org.apache.log4j.Logger;
import org.carrot2.clustering.lingo.LingoClusteringAlgorithm;
import org.carrot2.core.Cluster;
import org.carrot2.core.Controller;
import org.carrot2.core.ControllerFactory;
import org.carrot2.core.Document;
import org.carrot2.core.LanguageCode;
import org.carrot2.core.ProcessingResult;

import activity.web.db.dao.JSTreeDao;
import cn.zju.edu.blf.dao.GroupedInteraction;
import cn.zju.edu.blf.dao.MapKeyEntity;
import cn.zju.edu.manager.HistoryActivityManager;
import cn.zju.edu.util.CommonUtil;
import cn.zju.edu.util.DateUtil;
import cn.zju.edu.util.InteractionUtil;
import cn.zju.edu.util.QuerySeqUtil;

public class WebpageTitleProcesser {
	static Logger logger = Logger.getLogger(WebpageTitleProcesser.class.getName());
	
	static List<String> CLUSTER_FILTER_WORDS = new ArrayList<String>();
	
	static{
		readFilterWords();
	}
	
	private static void readFilterWords()
	{
		try
		{
			BufferedReader br = new BufferedReader(new InputStreamReader(HistoryActivityManager.class.getResourceAsStream("/config/cluster_filter_words.txt"))); 
			
			String line = br.readLine();
			while(line != null && !"".equals(line))
			{
				CLUSTER_FILTER_WORDS.add(line);
				
				line = br.readLine();
			}
			
			br.close();
			
		}catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	
	private boolean eclipseCheck = false;
	private boolean browserCheck = true;
	private boolean officeCheck = false;
	private boolean vsCheck = false;
	private boolean otherCheck = false;
	
	private List<Cluster> clusters = new ArrayList<Cluster>();
	//private Map<String, Integer> titleWordMap = new HashMap<String, Integer>();
	
	public String process(String timestamp, List<GroupedInteraction> interactions)
	{
		process1(timestamp, interactions);
		return process2();
	}
	
	protected String process2()
	{
		List<JSTreeDao> nodes = new ArrayList<JSTreeDao>();
		List<MapKeyEntity> words = new ArrayList<MapKeyEntity>();
		for(int i=0; i<clusters.size(); i++)
		{
			
			String name = clusters.get(i).getLabel();
			double score = clusters.get(i).getScore();
			if("Other Topics".equals(name)) continue;
			
			JSTreeDao node = new JSTreeDao();
			node.setId(name);
			node.setParent("#");
			node.setText(name);
			nodes.add(node);
			
			MapKeyEntity mke = new MapKeyEntity();
			mke.setKey(name);
			mke.setValue(score);
			words.add(mke);
			
			for(int j=0; j<clusters.get(i).getAllDocuments().size(); j++)
			{
				Document doc = clusters.get(i).getAllDocuments().get(j);
				JSTreeDao subNode = new JSTreeDao();
				subNode.setId(name+"_"+j);
				subNode.setParent(name);
				subNode.setText(doc.getContentUrl());
				nodes.add(subNode);
			}
		}
		
		HashMap<String, Object> map = new HashMap<String, Object>();
		map.put("topic", nodes);
		map.put("words", words);
		
		JSONObject jsonObject = JSONObject.fromObject(map); 
		
		return jsonObject.toString();
	}
	
	protected  void process1(String timestamp, List<GroupedInteraction> interactions) 
	{
		ArrayList<Document> documents = new ArrayList<Document>();
		
		for(GroupedInteraction g : interactions)
		{
			if(!isFilter(g.getApplication()))
			{
				if(timestamp != null && !"".equals(timestamp))
				{
					if(g.getDetails().size() >= 0)
					{
						String gt = g.getDetails().get(g.getDetails().size()-1).getTime();
						
						if( DateUtil.calcInterval(timestamp, gt) < 0 )
						{
							continue;
						}
					}
				}
				
				String title = g.getTitle();
				for(int i=0; i<CLUSTER_FILTER_WORDS.size(); i++)
				{
					if(g.getTitle().contains(CLUSTER_FILTER_WORDS.get(i)))
					{
						title = title.replaceAll(CLUSTER_FILTER_WORDS.get(i), "");
					}
				}
				if(title.startsWith("http://"))
				{
					continue;
				}
				
				/*
				List<String> words = QuerySeqUtil.segQuery(title);
				for(int i=0; i<words.size(); i++)
				{
					String w = words.get(i);
					if(CommonUtil.isNumeric(w)) continue;
						
					if(titleWordMap.containsKey(w))
					{
						titleWordMap.put(w, titleWordMap.get(w)+1);
					}
					else
					{
						titleWordMap.put(w, 1);
					}
					
				}
				*/
				
				Document doc = new Document(title,title,g.getTitle());
			    doc.setLanguage(LanguageCode.CHINESE_SIMPLIFIED);
			    documents.add(doc);
			}
		}
		
		final Controller controller = ControllerFactory.createSimple();
		 
		 LingoClusteringAlgorithm al = new LingoClusteringAlgorithm();
		 al.desiredClusterCountBase = 30;
		 
		 Map<String, Object> attributes = new HashMap<String, Object>(); 
		
		 attributes.put("LingoClusteringAlgorithm.desiredClusterCountBase", 10); 
		 
        controller.init(attributes);
        
        final ProcessingResult byTopicClusters = controller.process(documents, "*",LingoClusteringAlgorithm.class);
		 
		         
		clusters = byTopicClusters.getClusters();
	}
	
	public boolean isFilter(String app)
	{
		if(InteractionUtil.isBrowser(app) && isBrowserCheck())
		{
			return false;
		}
		
		if(eclipseCheck && isEclipse(app))
		{
			return false;
		}
		
		if(officeCheck && isOffice(app))
		{
			return false;
		}
		
		if(vsCheck && isVS(app))
		{
			return false;
		}
		
		if(otherCheck && isOther(app))
		{
			return false;
		}
		
		return true;
	}
	
	private boolean isEclipse(String app)
	{
		return "eclipse.exe".equalsIgnoreCase(app) || "javaw.exe".equalsIgnoreCase(app);
	}
	
	private boolean isOffice(String app)
	{
		return "winword.exe".equalsIgnoreCase(app) || "excel.exe".equalsIgnoreCase(app) || "powerpoint.exe".equalsIgnoreCase(app);
	}
	
	private boolean isVS(String app)
	{
		return "deven.exe".equalsIgnoreCase(app);
	}
	
	private boolean isOther(String app)
	{
		return !(InteractionUtil.isBrowser(app) || 
				isEclipse(app) || isOffice(app) || isVS(app));
	}
	
	public boolean isEclipseCheck() {
		return eclipseCheck;
	}
	
	public void setEclipseCheck(boolean eclipseCheck) {
		this.eclipseCheck = eclipseCheck;
	}
	
	public void setEclipseCheck(String eclipseCheck) {
		this.eclipseCheck = "true".equalsIgnoreCase(eclipseCheck);
	}
	
	public boolean isBrowserCheck() {
		return browserCheck;
	}

	public void setBrowserCheck(boolean browserCheck) {
		this.browserCheck = browserCheck;
	}
	
	public void setBrowserCheck(String browserCheck) {
		this.browserCheck = "true".equalsIgnoreCase(browserCheck);
	}
	
	public boolean isOfficeCheck() {
		return officeCheck;
	}

	public void setOfficeCheck(boolean officeCheck) {
		this.officeCheck = officeCheck;
	}
	
	public void setOfficeCheck(String officeCheck) {
		this.officeCheck = "true".equalsIgnoreCase(officeCheck);
	}
	
	public boolean isVsCheck() {
		return vsCheck;
	}

	public void setVsCheck(boolean vsCheck) {
		this.vsCheck = vsCheck;
	}
	
	public void setVsCheck(String vsCheck) {
		this.vsCheck = "true".equalsIgnoreCase(vsCheck);
	}
	
	public boolean isOtherCheck() {
		return otherCheck;
	}

	public void setOtherCheck(boolean otherCheck) {
		this.otherCheck = otherCheck;
	}
	
	public void setOtherCheck(String otherCheck) {
		this.otherCheck = "true".equalsIgnoreCase(otherCheck);;
	}
}
