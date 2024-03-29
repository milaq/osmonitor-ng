/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.milaq.osmonitor.networks;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.URL;
import java.util.HashMap;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

import de.milaq.osmonitor.*;
import de.milaq.osmonitor.preferences.Preferences;


public class NetworkList extends MapActivity
{
	private JNIInterface JNILibrary = JNIInterface.getInstance();
	private NetworkListAdapter UpdateInterface = null;
    private NetworkInfoQuery NetworkInfo = null;
	
	private TextView EmptyMsg = null;
	
	private ProgressDialog ProcDialog = null;
    private EventHandler ProcHandler = null;
    private QueryWhois ProcThread = null;

	private boolean UseWhois = true;
	private boolean IP6to4 = true;
	private boolean RDNS = false;
	
	private WeakReference<FrameLayout> MapBodyRef = null;
	private MapView GeoIPMap = null;
    
	private Runnable runnable = new Runnable() {
		public void run() {
	        
			if(JNILibrary.doDataLoad() == 1)
			{
		    	JNILibrary.doDataSwap();
		    	UpdateInterface.notifyDataSetChanged();
		    	
				if(EmptyMsg != null)
					EmptyMsg.setText("");
			}
	        
	        handler.postDelayed(this, 1000);
		}
	};   
	
	Handler handler = new Handler();
	
    public class EventHandler extends Handler 
    {
        public void handleMessage(Message msg) 
        {
        	if(msg.obj != null)
        	{
        		CacheQuery WhoisQuery = (CacheQuery) msg.obj;
        		showWhois(WhoisQuery.Msg, WhoisQuery.Longtiude, WhoisQuery.Latitude);
        	}
        	
        	if(ProcDialog != null)
        	{
            	ProcDialog.dismiss();
            	ProcDialog = null;
        	}
        }
    }
    
    class CacheQuery 
    {
    	public String Msg;
    	public double Longtiude;
        public double Latitude; 
    }
    
    private final HashMap<String, CacheQuery> CacheWhois = new HashMap<String, CacheQuery>();
	class QueryWhois extends Thread 
	{
		public String QueryIP = ""; 
		public Boolean ForceStop = false;
		@Override
        public void run() 
		{
			if(CacheWhois.get(QueryIP) != null)
			{
				if(!ForceStop)
				{
					CacheQuery WhoisQuery = CacheWhois.get(QueryIP);
					Message QueryResult = new Message();
					QueryResult.obj = WhoisQuery;
					ProcHandler.sendMessage(QueryResult);
				}
				return;
			}
			
			StringBuilder whoisInfo = new StringBuilder();
	        CacheQuery WhoisQuery = new CacheQuery();
			try {
				/* Create a URL we want to load some xml-data from. */
	            URL url = new URL("http://xml.utrace.de/?query="+QueryIP);
 
	            /* Get a SAXParser from the SAXPArserFactory. */
	            SAXParserFactory spf = SAXParserFactory.newInstance();
	            SAXParser sp = spf.newSAXParser();

	            /* Get the XMLReader of the SAXParser we created. */
	            XMLReader xr = sp.getXMLReader();
	            
	            /* Create a new ContentHandler and apply it to the XML-Reader*/
	            WhoisSAX SAXHandler = new WhoisSAX();
	            xr.setContentHandler(SAXHandler);
	               
	            InputStream urlData = url.openStream();
	            /* Parse the xml-data from our URL. */
	            xr.parse(new InputSource(urlData));
	            /* Parsing has finished. */
	            urlData.close();

	            /* Our ExampleHandler now provides the parsed data to us. */
	            WhoisSAXDataSet parsedDataSet = SAXHandler.getParsedData();

	            /* Set the result to be displayed in our GUI. */
	            whoisInfo.append(parsedDataSet.toString());

	            String WhoisMsg = whoisInfo.toString();

		        try
		        {
		        	WhoisMsg = "DNS: "+InetAddress.getByName(QueryIP).getHostName()+
		        			   "\n" + WhoisMsg;
				} catch (Exception e) {}

				WhoisQuery.Msg = WhoisMsg;
				WhoisQuery.Longtiude = parsedDataSet.getMapLongtiude();
				WhoisQuery.Latitude = parsedDataSet.getMapnLatitude();
		        CacheWhois.put(QueryIP, WhoisQuery);
	        } 
			catch (Exception e) 
	        {
				WhoisQuery.Msg = "Query failed!";
	        }  
			
			if(!ForceStop) {
				Message QueryResult = new Message();
				QueryResult.obj = WhoisQuery;
				ProcHandler.sendMessage(QueryResult);
			}

			return;
		}
    }
	
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        
        // Use a custom layout file
        setContentView(R.layout.networklayout);

		GeoIPMap = new MapView(this, "0N4HYg91PN1-cGgp3exBmvC1AdzeiGYzp7C3V7g");
		//GeoIPMap = new MapView(this, "0N4HYg91PN19P2R67mtD2NGBl3ce5DxqXlmH6TA");
		GeoIPMap.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, 180));

		ProcHandler = new EventHandler();
        
		ListView InternalList = (ListView) findViewById(R.id.networklist);
        InternalList.setEmptyView(findViewById(R.id.empty));
        InternalList.setAdapter(new NetworkListAdapter(this));
        InternalList.setOnItemClickListener(InternalListListener);
        UpdateInterface = (NetworkListAdapter) InternalList.getAdapter();
        
        EmptyMsg = (TextView) findViewById(R.id.empty);
        
        NetworkInfo = NetworkInfoQuery.getInstance(); 
    }
    
    public boolean onCreateOptionsMenu(Menu optionMenu) 
    {
     	super.onCreateOptionsMenu(optionMenu);
     	optionMenu.add(0, 1, 0, getResources().getString(R.string.menu_options));
     	optionMenu.add(0, 2, 0, getResources().getString(R.string.menu_logexport));
       	optionMenu.add(0, 4, 0, getResources().getString(R.string.menu_help));
       	optionMenu.add(0, 5, 0, getResources().getString(R.string.menu_forceexit));
        
    	return true;
    }
    
	

	private void restorePrefs()
    { 
		// load settings
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		
		try {
			JNILibrary.doDataTime(Integer.parseInt(settings.getString(Preferences.PREF_UPDATE, "2")));
		} catch(Exception e) {}

		IP6to4 = settings.getBoolean(Preferences.PREF_IP6to4, true); 
		UseWhois = settings.getBoolean(Preferences.PREF_USEWHOIS, true);
		RDNS = settings.getBoolean(Preferences.PREF_RDNS, false);
		
		if(IP6to4 == true)
			JNILibrary.SetNetworkIP6To4(1);
		else
			JNILibrary.SetNetworkIP6To4(0);
		
        if(settings.getBoolean(Preferences.PREF_STATUSBAR, false))
        {
        	if(OSMonitorService.getInstance() == null)
        		startService(new Intent(this, OSMonitorService.class));
        	else
        		OSMonitorService.getInstance().Notify();
        }
        else
        	if(OSMonitorService.getInstance() != null)
        		OSMonitorService.getInstance().stopSelf();

    }
	
	
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
    	restorePrefs();
    }
    
    @Override
    protected Dialog onCreateDialog(int id) 
    {
    	switch (id)
    	{
    	case 0:
    		AlertDialog.Builder HelpWindows = new AlertDialog.Builder(this);
    		HelpWindows.setTitle(R.string.app_name);
			HelpWindows.setMessage(R.string.help_info);
			HelpWindows.setPositiveButton(R.string.button_close,
			   new DialogInterface.OnClickListener() {
				   public void onClick(DialogInterface dialog, int whichButton) { }
				}
			);

   	        WebView HelpView = new WebView(this);
            HelpView.loadUrl("http://wiki.android-os-monitor.googlecode.com/hg/phonehelp.html?r=b1c196ee43855882e59ad5b015b953d62c95729d");
            HelpWindows.setView(HelpView);

        	return HelpWindows.create(); 
        	
    	case 1:
    		LinearLayout Layout = new LinearLayout(this);
    		Layout.setOrientation(LinearLayout.VERTICAL);
    		Layout.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT,
    									LayoutParams.FILL_PARENT));
    		
    		EditText FileName = new EditText(this);
    		FileName.setId(100);

    		CheckBox UseHTML = new CheckBox(this);
    		UseHTML.setId(200);
    		UseHTML.setText("HTML");
    		
    		Layout.addView(FileName, 0);
    		Layout.addView(UseHTML, 1);
    		
    		return new AlertDialog.Builder(this)
            .setTitle(R.string.common_exportfile_title)
            .setView(Layout)
            .setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                	String FileName = ((EditText)((AlertDialog)dialog).findViewById(100)).getText().toString();
                	Boolean useHTML = ((CheckBox)((AlertDialog)dialog).findViewById(200)).isChecked();
                	SaveLog(FileName, useHTML);
                }
            })
            .setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                }
            })
            .create();    	}
    	
    	return null;        
    } 
    
    private String GetAppInfo(String AppName, int UID)
    {
    	PackageManager AppInfo = getPackageManager();	
    	
		PackageInfo appPackageInfo = null;
		String PackageName = null;
		if(AppName.contains(":"))
			PackageName = AppName.substring(0, AppName.indexOf(":"));
		else
			PackageName = AppName;
		
		// for system user
		try {  
			appPackageInfo = AppInfo.getPackageInfo(PackageName, 0);
		} catch (NameNotFoundException e) {}
		
		if(appPackageInfo == null && UID >0)
		{
			String[] subPackageName = AppInfo.getPackagesForUid(UID);
				
			if(subPackageName != null)
			{
				for(int PackagePtr = 0; PackagePtr < subPackageName.length; PackagePtr++)
				{
					if (subPackageName[PackagePtr] == null)
						continue;
					
					try {  
						appPackageInfo = AppInfo.getPackageInfo(subPackageName[PackagePtr], 0);
						PackagePtr = subPackageName.length;
					} catch (NameNotFoundException e) {}						
				}
			}
		}    	
		if(appPackageInfo != null)
			return appPackageInfo.applicationInfo.loadLabel(AppInfo).toString();
		else if (UID == 0)
			return "System";
		else
			return PackageName;
    }
    
    private void SaveLog(String FileName, Boolean useHTML)
    {
    	if(FileName.trim().equals(""))
    		return;
    	
    	try {
        	File LogFile = new File("/sdcard/" + FileName);
    		
        	if (LogFile.exists())
        	{
        		new AlertDialog.Builder(this)
    		   		.setTitle(R.string.app_name)
    		   		.setMessage(R.string.common_exportexist_title)
    		   		.setPositiveButton(R.string.button_ok,
    		   				new DialogInterface.OnClickListener() {
    		   			public void onClick(DialogInterface dialog, int whichButton) { } })
    		   		.create()
    		   		.show();
        		return;
        	}

        	int LogCount = JNILibrary.GetNetworkCounts();
        	LogFile.createNewFile();
        	FileWriter TextFile = new FileWriter(LogFile);
        	
        	if(useHTML)
        		TextFile.write("<html>\n<body>\n<table>\n");
        	
        	for(int i = 0; i < LogCount;i++)
        	{
        		String TextLine = "";
       			if(useHTML)
       				TextLine = "<tr><td>"+JNILibrary.GetNetworkProtocol(i)+ "</td><td>"
       							+ JNILibrary.GetNetworkLocalIP(i)+":"
       							+ JNILibrary.GetNetworkLocalPort(i)+"</td><td>"
       							+ JNILibrary.GetNetworkRemoteIP(i)+":"
       							+ JNILibrary.GetNetworkRemotePort(i)+"</td><td>"
       							+ JNILibrary.GetNetworkStatus(i) + "</td></tr>\n";
       			else
       				TextLine =  JNILibrary.GetNetworkProtocol(i)+ " "
       							+ JNILibrary.GetNetworkLocalIP(i)+":"
       							+ JNILibrary.GetNetworkLocalPort(i)+" "
       							+ JNILibrary.GetNetworkRemoteIP(i)+":"
       							+ JNILibrary.GetNetworkRemotePort(i)+" "
       							+ JNILibrary.GetNetworkStatus(i) + "\n";
        		TextFile.write(TextLine);
        	}

        	if(useHTML)
        		TextFile.write("</table>\n</body>\n</html>\n");

        	TextFile.close();

    	} catch (Exception e) {
    		new AlertDialog.Builder(this)
	   		.setTitle(R.string.app_name)
	   		.setMessage(e.getMessage())
	   		.setPositiveButton(R.string.button_ok,
	   				new DialogInterface.OnClickListener() {
	   			public void onClick(DialogInterface dialog, int whichButton) { } })
	   		.create()
	   		.show();

    		return;
    	}
    	
  		new AlertDialog.Builder(this)
   		.setTitle(R.string.app_name)
   		.setMessage(R.string.common_exportdone_title)
   		.setPositiveButton(R.string.button_ok,
   				new DialogInterface.OnClickListener() {
   			public void onClick(DialogInterface dialog, int whichButton) { } })
   		.create()
   		.show();

  		return;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
    	
        super.onOptionsItemSelected(item);
        switch(item.getItemId())
        {
        case 1:
            Intent launchPreferencesIntent = new Intent().setClass( this, Preferences.class);
            startActivityForResult(launchPreferencesIntent, 0);
        	break;
        	
        	
        case 2:
        	this.showDialog(1);
        	break;
        	
        case 4:
        	this.showDialog(0);
        	break;

        case 5:
        	if(OSMonitorService.getInstance() != null)
        		OSMonitorService.getInstance().stopSelf();

        	CommonUtil.killSelf(this);

        	break;
        }
        
        return true;
    }
    @Override
    public void onPause() 
    {
    	if(ProcDialog != null)
    	{
        	ProcDialog.dismiss();
        	ProcDialog = null;
    	}

    	handler.removeCallbacks(runnable);
    	JNILibrary.doTaskStop();
    	super.onPause();
    }

    @Override
    protected void onResume() 
    {    
        restorePrefs();
    	JNILibrary.doTaskStart(JNILibrary.doTaskNetwork);
    	handler.post(runnable);
    	super.onResume();
    }
    
    
    private OnItemClickListener InternalListListener = new OnItemClickListener()
    {
    	@Override
    	public void onItemClick(AdapterView<?> l, View v, int position, long id) 
    	{  
    		if(!UseWhois || JNILibrary.GetNetworkRemoteIP(position).equals("0.0.0.0"))
    			return;
    		
    		String QueryIP = "";
       		if(JNILibrary.GetNetworkProtocol(position).equals("TCP6") ||
        			JNILibrary.GetNetworkProtocol(position).equals("UDP6"))
        		{
        			QueryIP = JNILibrary.GetNetworkRemoteIP(position);
        			QueryIP = QueryIP.replaceFirst("ffff:", "");
        			QueryIP = QueryIP.replaceFirst("::", "");
        		}
        		else
        			QueryIP = JNILibrary.GetNetworkRemoteIP(position);
       		
       		if(QueryIP.equals("0.0.0.0"))
       			return;
       		
       		if(QueryIP.equals("88.198.156.18"))
       		{
    			Builder Alert = new Builder(NetworkList.this);
    			Alert.setTitle("Whois API IP");
    			Alert.setMessage("http://en.utrace.de/api.php");
    			Alert.show();
    			return;
       		}
    		
    		// show progress dialog
    		ProcDialog = ProgressDialog.show( NetworkList.this, getResources().getText(R.string.pref_whoisdialog_title),
    					getResources().getText(R.string.message_waiting), true);
    		
    		ProcDialog.setOnCancelListener(new DialogInterface.OnCancelListener() 
    		{
    			public void onCancel(DialogInterface dialog) 
    			{
    				if(ProcThread != null) {
    					ProcThread.ForceStop = true;
    					ProcThread.stop();
    					ProcThread = null;
    				}
    			}
    		});
    		ProcDialog.setCancelable(true);

    		ProcThread = new QueryWhois();
			ProcThread.QueryIP = QueryIP;
    		ProcThread.start();
    	}
    };
    
    public void showWhois(String Msg, double Latitude, double Longitude)
    {

    	/*TextView MsgBody = new TextView(this);
    	MsgBody.setText(Msg);
    	MsgBody.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT,
    								LayoutParams.WRAP_CONTENT));
    	MapBody = new TableLayout(this);
    	MapBody.setGravity(Gravity.CENTER);
    	MapBody.addView(MsgBody);
    	*/
    	
    	FrameLayout MapBody = new FrameLayout(this);	
   		MapBodyRef = new WeakReference<FrameLayout>(MapBody);
    	
        MapBody.addView(GeoIPMap);
        MapController GeoIPControl = GeoIPMap.getController();

        GeoPoint MapPoint = new GeoPoint((int)Longitude,(int)Latitude);
		GeoIPControl.setZoom(8);
        GeoIPControl.animateTo(MapPoint);
        GeoIPControl.setCenter(MapPoint);
    	GeoIPMap.getOverlays().add(new MapOverlay(this, MapPoint, R.drawable.point));

    	AlertDialog.Builder WhoisAlert = new AlertDialog.Builder(this);
    	WhoisAlert.setTitle("Whois");
    	WhoisAlert.setMessage(Msg);
    	WhoisAlert.setView(MapBody);
    	WhoisAlert.setOnCancelListener(new DialogInterface.OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
		    	if(MapBodyRef != null && MapBodyRef.get() != null) {
		    		MapBodyRef.get().removeAllViews();
		    	}
				
			}
		});
    	WhoisAlert.show();
    	
    }
    
    
    
    /**
     * A sample ListAdapter that presents content
     * from arrays of speeches and text.
     *
     */
    private class NetworkListAdapter extends BaseAdapter {
        public NetworkListAdapter(Context context)
        {
           // mContext = context;
        }

        /**
         * The number of items in the list is determined by the number of speeches
         * in our array.
         * 
         * @see android.widget.ListAdapter#getCount()
         */
        public int getCount() {
            return JNILibrary.GetNetworkCounts();
        }

        /**
         * Since the data comes from an array, just returning
         * the index is sufficient to get at the data. If we
         * were using a more complex data structure, we
         * would return whatever object represents one 
         * row in the list.
         * 
         * @see android.widget.ListAdapter#getItem(int)
         */
        public Object getItem(int position) {
            return position;
        }

        /**
         * Use the array index as a unique id.
         * @see android.widget.ListAdapter#getItemId(int)
         */
        public long getItemId(int position) {
            return position;
        }

        /**
         * Make a SpeechView to hold each row.
         * @see android.widget.ListAdapter#getView(int, android.view.View, android.view.ViewGroup)
         */
        public View getView(int position, View convertView, ViewGroup parent) {
        	NetworkDetailView sv;

        	String LocalDNS = "";
    		String RemoteDNS = "";
    		
            if(RDNS)
            {
            	if(JNILibrary.GetNetworkProtocol(position).equals("TCP6") ||
            		JNILibrary.GetNetworkProtocol(position).equals("UDP6"))
            	{
            		String QueryIPv4 = JNILibrary.GetNetworkLocalIP(position);
        			QueryIPv4 = QueryIPv4.replaceFirst("ffff:", "");
        			QueryIPv4 = QueryIPv4.replaceFirst("::", "");
            		NetworkInfo.doCacheInfo(QueryIPv4);
        			LocalDNS = NetworkInfo.GetDNS(QueryIPv4);

            		QueryIPv4 = JNILibrary.GetNetworkRemoteIP(position);
        			QueryIPv4 = QueryIPv4.replaceFirst("ffff:", "");
        			QueryIPv4 = QueryIPv4.replaceFirst("::", "");
        			NetworkInfo.doCacheInfo(QueryIPv4);
            		RemoteDNS = NetworkInfo.GetDNS(QueryIPv4);
            	}
            	else
            	{
            		NetworkInfo.doCacheInfo(JNILibrary.GetNetworkRemoteIP(position));
            		RemoteDNS = NetworkInfo.GetDNS(JNILibrary.GetNetworkRemoteIP(position));
            		NetworkInfo.doCacheInfo(JNILibrary.GetNetworkLocalIP(position));
            		LocalDNS = NetworkInfo.GetDNS(JNILibrary.GetNetworkLocalIP(position));
            	}
            }
            

            String AppName = GetAppInfo(JNILibrary.GetProcessNamebyUID(JNILibrary.GetNetworkUID(position)),
            							JNILibrary.GetNetworkUID(position));

            if (convertView == null) {
            	if(RDNS)
            	{
            		sv = new NetworkDetailView(getApplication(),
            				 JNILibrary.GetNetworkProtocol(position),
            				 LocalDNS,
            				 JNILibrary.GetNetworkLocalPort(position),
            				 RemoteDNS,
            				 JNILibrary.GetNetworkRemotePort(position),
            				 JNILibrary.GetNetworkStatus(position),
            				 AppName,
            				 position);
            	}
            	else
                    sv = new NetworkDetailView(getApplication(),
                    		 JNILibrary.GetNetworkProtocol(position),
		                     JNILibrary.GetNetworkLocalIP(position),
							 JNILibrary.GetNetworkLocalPort(position),
		                     JNILibrary.GetNetworkRemoteIP(position),
							 JNILibrary.GetNetworkRemotePort(position),
							 JNILibrary.GetNetworkStatus(position),
							 AppName,
							 position);

            } else {
                sv = (NetworkDetailView)convertView;
                if(RDNS)
                	sv.setContext(JNILibrary.GetNetworkProtocol(position),
                			  LocalDNS,
							  JNILibrary.GetNetworkLocalPort(position),
					          RemoteDNS,
 							  JNILibrary.GetNetworkRemotePort(position),
						 	  JNILibrary.GetNetworkStatus(position),
							  AppName,
						 	  position);
                else
                	sv.setContext(JNILibrary.GetNetworkProtocol(position),
              			  	  JNILibrary.GetNetworkLocalIP(position),
							  JNILibrary.GetNetworkLocalPort(position),
					          JNILibrary.GetNetworkRemoteIP(position),
							  JNILibrary.GetNetworkRemotePort(position),
						 	  JNILibrary.GetNetworkStatus(position),
						 	  AppName,
						 	  position);
                	
            }
            
            return sv;
        }

        /**
         * Remember our context so we can use it when constructing views.
         */
        //private Context mContext;
    }
    
    /**
     * We will use a SpeechView to display each speech. It's just a LinearLayout
     * with two text fields.
     *
     */
    private class NetworkDetailView extends TableLayout {

    	private TableRow ConnectionRow;
    	private TextView ProtocolField;
    	private TextView IPField;
    	private TextView StatusField;
    	private TableRow ExtendRow;
    	private TextView APPField;
    	
        public NetworkDetailView(Context context,String Protocol, String LocalIP, int LocalPort,
        							String RemoteIP, int RemotePort, String Status, String AppName, int position) 
        {
            super(context);
            this.setColumnStretchable(1, true);
            this.setOrientation(VERTICAL);
            
            ProtocolField = new TextView(context);
            IPField = new TextView(context);
            StatusField = new TextView(context);
            APPField = new TextView(context);

            ProtocolField.setGravity(Gravity.LEFT);
            ProtocolField.setPadding(3, 3, 3, 3);
            if(CommonUtil.getScreenSize()== 2)
                ProtocolField.setWidth(70);
            else if(CommonUtil.getScreenSize() == 0)
            	ProtocolField.setWidth(32);
            else
            	ProtocolField.setWidth(45);

            IPField.setGravity(Gravity.LEFT);
            IPField.setPadding(3, 3, 3, 3);
            IPField.setWidth(getWidth()-160);

            StatusField.setGravity(Gravity.LEFT);
            StatusField.setPadding(3, 3, 3, 3);

            if(CommonUtil.getScreenSize() == 2)
                StatusField.setWidth(140);
            else if(CommonUtil.getScreenSize() == 0)
            	StatusField.setWidth(75);
            else
            	StatusField.setWidth(95);
            
            ProtocolField.setText(Protocol);
            StatusField.setText(Status);

            if(RemotePort == 0)
           		IPField.setText(LocalIP+":"+LocalPort+"\n"+RemoteIP+":*");
            else
           		IPField.setText(LocalIP+":"+LocalPort+"\n"+RemoteIP+":"+RemotePort);
            	 
            ConnectionRow = new TableRow(context);
            ConnectionRow.addView(ProtocolField);
            ConnectionRow.addView(IPField);
            ConnectionRow.addView(StatusField);
            addView(ConnectionRow);

            ExtendRow = new TableRow(context);
            APPField.setText(AppName);
            ExtendRow.addView(new TextView(context));
            ExtendRow.addView(APPField);
            ExtendRow.addView(new TextView(context));
            addView(ExtendRow);

            TableRow.LayoutParams RowParams = (TableRow.LayoutParams)APPField.getLayoutParams();
            RowParams.span = 2;
            APPField.setLayoutParams(RowParams);

            if(position % 2 == 0)
	     		setBackgroundColor(0x80444444);
	     	else
	     		setBackgroundColor(0x80000000);
 
        }
        
		public void setContext(String Protocol, String LocalIP, int LocalPort,
								String RemoteIP, int RemotePort, String Status, String AppName, int position) 
		{
			
            ProtocolField.setText(Protocol);
            StatusField.setText(Status);

            if(RemotePort == 0)
           		IPField.setText(LocalIP+":"+LocalPort+"\n"+RemoteIP+":*");
            else
           		IPField.setText(LocalIP+":"+LocalPort+"\n"+RemoteIP+":"+RemotePort);
            
            APPField.setText(AppName);
                      
	     	if(position % 2 == 0)
	     		setBackgroundColor(0x80444444);
	     	else
	     		setBackgroundColor(0x80000000);
		}
    }

	@Override
	protected boolean isRouteDisplayed() {
		// TODO Auto-generated method stub
		return false;
	}
}
