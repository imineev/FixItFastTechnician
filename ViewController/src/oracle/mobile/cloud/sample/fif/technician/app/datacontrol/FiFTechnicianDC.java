package oracle.mobile.cloud.sample.fif.technician.app.datacontrol;

import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import oracle.adf.model.datacontrols.device.DeviceManagerFactory;
import oracle.adf.model.datacontrols.device.Location;

import oracle.adfmf.framework.api.AdfmfJavaUtilities;
import oracle.adfmf.framework.api.JSONBeanSerializationHelper;
import oracle.adfmf.framework.exception.AdfException;
import oracle.adfmf.java.beans.PropertyChangeListener;
import oracle.adfmf.java.beans.PropertyChangeSupport;
import oracle.adfmf.java.beans.ProviderChangeListener;
import oracle.adfmf.java.beans.ProviderChangeSupport;
import oracle.adfmf.json.JSONObject;

import oracle.mobile.cloud.sample.fif.technician.app.data.collections.Incidents;
import oracle.mobile.cloud.sample.fif.technician.app.data.entities.Incident;
import oracle.mobile.cloud.sample.fif.technician.app.images.FiFImageHandler;
import oracle.mobile.cloud.sample.fif.technician.app.log.AppLogger;
import oracle.mobile.cloud.sample.fif.technician.constants.FiFConstants;
import oracle.mobile.cloud.sample.fif.technician.maf.RequestContext;
import oracle.mobile.cloud.sample.fif.technician.maf.ResponseContext;
import oracle.mobile.cloud.sample.fif.technician.maf.RestClient;
import oracle.mobile.cloud.sample.fif.technician.mcs.analytics.Analytics;
import oracle.mobile.cloud.sample.fif.technician.mcs.analytics.Event;
import oracle.mobile.cloud.sample.fif.technician.mcs.analytics.Session;
import oracle.mobile.cloud.sample.fif.technician.mcs.log.FiFLogger;
import oracle.mobile.cloud.sample.fif.technician.mcs.mbe.FiFMBEConfig;
import oracle.mobile.cloud.sample.fif.technician.mcs.mbe.FiFMobileBackend;
import oracle.mobile.cloud.sample.fif.technician.mcs.notifications.Notifications;


/**
 * "THE ONE THAT RULES THEM ALL" - POJO Data Control that exposes MCS MBE communication to the user interface,
 * the binding layer and the managed bean layer. The following MCS functionality is exposed by this POJO DC
 *
 * 1. query allIncidents by technician or customer
 * 2. filter incident query using GPS coordinate data (Latitude, Longitude, separated by comma  E.g. 39.355589,-120.652492)
 * 3. Add custom analytic events and flush events to the MCS analytic engine
 * 4. Query single incident for update
 * 5. Display storage collection image for incident
 *
 * Question 1:  How big should or could a data control class be?
 * Answer   1:  A data control is comparable to a facade in POJO or EJB development and represents the API for the binding
 * layer to work with. As such, a dat control should be functional complete for the task at hand. In this sample
 * the task at hand is for the technician to view incident report data and to respond to them. This is a single
 * task that can be modeled in a single data control.
 *
 * Question 2:  You save data in a member variable. How much data can a data control hold?
 * Answer   2:  Device memory is the limit. When working with large data sets (say a hundred of records and more)
 * then its probably a good idea to fetch data from MCS and to store them in a local SQLite database.
 * From a performance perspective it makes sense to always keep the amount of data in memory that a
 * user actively works with.
 *
 * Question 3:  When is a data control released?
 * Answer   3:  Data controls are created per use and MAF feature, which means that instances are not shared across features.
 * As of MAF 2.1 there is no option to actively power-off a feature (dismiss it), which means that you should
 * expect the data control instance to live for a while. So the best strategy to be friendly to the device memory
 * is to use a SQLite database for your temporary data if different features work with teh same data control in
 * different instances of it. This way data is not duplicated in memory. A managed bean in application scope could
 * also be used to share data in memory. The recommendation should be to void data controls to hold data but to use
 * a shared storage like SQLite database or (for smaller data sets) managed beans in application scope. This way the
 * data control functionality reduces to one of a facade, which means tha memory should not become a problem at all.
 *
 * @author Frank Nimphius
 * @copyright Copyright (c) 2015 Oracle. All rights reserved.
 */
public class FiFTechnicianDC{
    
    private final String REST_CONNECTION_NAME = "FiFMBE";
    private final String FIF_CUSTOM_API_INCIDENTS_URI = "/mobile/custom/incident/incidents";
    
    
    protected ProviderChangeSupport providerChangeSupport = new ProviderChangeSupport(this);


     
    /*
     * This sample application uses more infrastructure than needed for a quick sample application. The Mobile Backend (MBE) instance 
     * is instantiated from a mobile configuration object that ensures the MBE configuration settings are accessible throughout the
     * application model files (e.g. Analytics and the Storage access)
     */
    private String mFifMobileBackendId = null;
    private String mFifMobileBackendName = null;
    private boolean mFifEnableAnalytics = true;
    private String mFifMobileBackendApplicationKey = null;
    private FiFMobileBackend mFifMobileBackend = null;
    private FiFMBEConfig mFifConfig = null;
    private Analytics mFifAnalytics = null;
    
    
    //Instead of throwing exceptions that are then displayed in an altert
    //these two properties help displaying user information in the view
    private String dcMessage = null;
    private boolean dcHasMessage = false;
    
    //the current incident list requires this parameter to be set so it can 
    //read a single incident report from MCS. Because the detail incident 
    //can alao be queried in response to a push notification, there is no
    //guarantee thee record is already queried. As such by design all details 
    //are re-queried using this parameter
    private String currentIncidentId = null;
    
    //collection exposed in list view
    private ArrayList<Incident> allIncidents = new ArrayList<Incident>();
    
    //the SRDetail page requires the longitude and latitide information as separate 
    //values. To simplify the code on the client and allow direct MAF binding access,
    //we introduce two variables technicianLocationLong and technicianLocationLat. As
    //a goodie, the intermidieat position as read from teh device is exposed by the 
    //technicianLocationLongLat variable in form of latitude, longitude String value
    private String technicianLocationLongLat = null;
    private String technicianLocationLong = null;
    private String technicianLocationLat = null;
        
    //to avoid unnecessary server ound trips, keep a hidden copy of the incident list queried from the 
    //server. This list then is used to filter list items in memory e.g. by status New, InProgress, Complete, All
    private ArrayList<Incident> mIncidentListCache = null;
    private PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    /* ***********************************************
     * CONSTRUCTOR
     * *********************************************** */    
        
    public FiFTechnicianDC() {                    
        super();        
        //read MBE related information from the MAF application preferences
        boolean preferenceSuccess = _initFormAplicationPreferences();        
        
        
        HashMap<String,String> customEventDef = new  HashMap<String,String>();
        
        customEventDef.put("action","application initialization"); 
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
        Date d = new Date();
        String timeStamp = sdf.format(d);
        
        customEventDef.put("timeStamp",timeStamp);            
        
        this.addCustomAnalyticEvent("DataControlEvent", customEventDef);
        
        
        //read the application preferences for the MBE ID, MBE name, the base URL and the Android and iOS client keys
        //if any of these information is missing, then the application cannot run. An error will be displayed on the 
        //first page. For this the dcErrorMessage and dcHasErrors variabes are exposed on the data control
        if(preferenceSuccess){
            
            customEventDef = new  HashMap<String,String>();
            
            customEventDef.put("action","loaded application preferences");
            customEventDef.put("mobile-backend-id",this.mFifMobileBackendId);
            customEventDef.put("mobile-backend-name",this.mFifMobileBackendName);
            customEventDef.put("application-key",this.mFifMobileBackendApplicationKey);                       
            
            d = new Date();
            timeStamp = sdf.format(d);
            
            customEventDef.put("timeStamp",timeStamp);            
            
            this.addCustomAnalyticEvent("DataControlEvent", customEventDef);
            
            _initMobileBackend();
            //the 
            resetDataControlMessages();
        }
        else{
            AppLogger.logSevereError("FIF Technician Application not correctly configured. Application cannot run", this.getClass().getSimpleName(), "FiFTechnicianDC - Constructor");
        }        
    }


    /* ***********************************************
     * PUBLIC METHODS EXPOSED ON THE DATA CONTROL PANEL
     * *********************************************** */
    
    
    /*
     * Custom FIF APIs
     */
   
    //method used by MAF binding
    public void setAllIncidents(ArrayList<Incident> incidents) {
        //the update is not through the user interface a but re-query from MCS. This method thus only 
        //triggers the refresh of the UI
        
        providerChangeSupport.fireProviderRefresh("allIncidents");
        
        
    }

    //method used by MAF binding
    public ArrayList<Incident> getAllIncidents() {
        //if this is the first time incidents are queried, or if the incident cache has been cleared, re-query 
        //incident reports from server
        if(this.mIncidentListCache == null){
          
            String username = (String) this.mFifConfig.getAuthenticatedUsername();                          
            if(username != null){
                //set query condition to user name. In the x-week schema, joe and jill have their query names set to <name>@fixit.com
                if(username.equalsIgnoreCase("joe")){
                    FiFLogger.logFine("Querying all incidents reported for user  = joe@fixit.com", this.getClass().getSimpleName(), "getAllIncidents");
                    this._qryIncidentsFromMcsForTechnician("joe@fixit.com");
                }
                else if (username.equalsIgnoreCase("jill")){
                    FiFLogger.logFine("Querying all incidents reported for user  = jill@fixit.com", this.getClass().getSimpleName(), "getAllIncidents");
                    this._qryIncidentsFromMcsForTechnician("jill@fixit.com");
                }
                else{
                    //note that this may not return values if there is no incident reported for this technician. So best is to use joe or jill 
                    //with the current x-week schema
                    FiFLogger.logFine("Querying all incidents reported for user  = "+username, this.getClass().getSimpleName(), "getAllIncidents");
                    this._qryIncidentsFromMcsForTechnician(username);
                } 
            }  
            
        } 
      return allIncidents;
    }
       
       
       
    /**
       * Queries the incidents in MCS by filter of contact (incident reportee), and (optionally) gps (Latitide, Longitude)). 
       * 
       * @param contact       Name of the incident report contact person
       */
       public ArrayList<Incident> qryIncidentsFromMcsForContact(String contact){
           
           if(contact == null){
               throw new AdfException("The name of the contact person cannot be null",AdfException.ERROR);
           }        
           ArrayList<Incident> contactIncidentList = this._getIncidentsFromMcsWithFilter(contact, null, this._getCurrentGeoPosition());
           return contactIncidentList;           
       }
                                          
    /**
     * In memory filtering of the incident reports by status: New, Open, Complete, All.
     */
    public void filterIncidentListInMemory(String filterValue){
        AppLogger.logFine("Start in Memory Filtering of incidents by filter criteria: "+filterValue , this.getClass().getSimpleName(), "filterIncidentListInMemory");               
        
        //is cache null?            
        if(this.mIncidentListCache == null){
           AppLogger.logFine("Cache not available for in memory filtering. Re-querying data from MCS ...." , this.getClass().getSimpleName(), "filterIncidentListInMemory");  
                
           //populate list. The incidents are kept in a local variable "allIncidents" so no need
           //to deal with a return value
           this.getAllIncidents();                
        }
            
        //if all should be shown, then re-read from cache
        if(filterValue .equalsIgnoreCase("ALL")){                
            //reset the content of all incidents
            this.allIncidents = new ArrayList<Incident>();
            this.allIncidents.addAll(new ArrayList<Incident>(this.mIncidentListCache));
        }
                
        //Loop over the list of incidents and remove all incident reports that have a status not matching the filter criteria
        else if(filterValue.equalsIgnoreCase("INPROGRESS") || filterValue.equalsIgnoreCase("COMPLETE") || filterValue.equalsIgnoreCase("NEW")){
            AppLogger.logFine("List size BEFORE aplying filter: "+allIncidents.size() , this.getClass().getSimpleName(), "filterIncidentListInMemory");  
            //reset the content of all incidents
            
             ArrayList<Incident> tempList = new ArrayList<Incident>();

            for(Incident incident : this.mIncidentListCache){
                //remove all objects that don't match the status to filter for
                if(incident.getStatus().equalsIgnoreCase(filterValue)){
                    tempList.add(incident);
                }
            }
            this.allIncidents = new ArrayList<Incident>(tempList);
            AppLogger.logFine("List size AFTER aplying filter: "+allIncidents.size() , this.getClass().getSimpleName(), "_filterIncidentListInMemory");                         
        }
        providerChangeSupport.fireProviderRefresh("allIncidents");
    }  
     
    /**
     * Updates the status of a specified incident 
     * 
     * @param incidentId required parameter ID that identifies the incident to update in MCS
     * @param status "Complete", "InProgress", "New", "All"
     * @param notes Comments
     * @return an incident in a list to display on an AMX page
     */
    public void updateIncidentStatus(String incidentId, String status, String notes){
        
        resetDataControlMessages();
        
        if(incidentId == null){
            AppLogger.logSevereError("Incident id in call to status update cannot be null", this.getClass().getSimpleName(), "updateIncidentStatus");                
        }
        
        String updateURI = FIF_CUSTOM_API_INCIDENTS_URI+"/"+incidentId+"/status";
        
        //request context sets header application/json by default and also add empty payload
        RequestContext request = new RequestContext();
        request.setConnectionName(REST_CONNECTION_NAME);
        request.setHttpMethod(RequestContext.HttpMethod.PUT);
        request.setRequestURI(updateURI);
        
        String payload = "{\"Status\": \""+status+"\", \"Notes\": \""+notes+"\"}";
        
        AppLogger.logFine("Sending REST request to URI: "+request.getRequestURI()+" with payload of: "+payload , this.getClass().getSimpleName(), "updateIncidentStatus");               
        
        request.setPayload(payload);
        request.setRequestURI(updateURI);
        
        HashMap<String, String> httpHeaders = new HashMap<String, String>();
        httpHeaders.put("Oracle-Mobile-Backend-Id", this.mFifMobileBackend.getMbeConfiguration().getMobileBackendId());
        httpHeaders.put("Authorization", this.mFifMobileBackend.getMbeConfiguration().getOauthHttpHeaderToken());
        httpHeaders.put("Content-Type", "application/json");
        
        request.setHttpHeaders(httpHeaders);        
        
        try {       
            
           ResponseContext response = RestClient.sendForStringResponse(request);           
           //its a PUT request and we expect http status 200 to indicate success
           if(response.getResponseStatus() == ResponseContext.STATUS_RESPONSE_OK){
               AppLogger.logFine("REST response: http-"+response.getResponseStatus()+" "+response.getResponsePayload() , this.getClass().getSimpleName(), "updateIncidentStatus");               
               this.setDcMessage("Update Sucessful.");
               this.setDcHasMessage(true);
           }
           else{
             AppLogger.logSevereError("Request failed with status: "+response.getResponseStatus()+" Message is:"+response.getResponsePayload(), this.getClass().getSimpleName(), "updateIncidentStatus");                
             
             this.setDcMessage("Update Failed with error: "+response.getResponsePayload());
             this.setDcHasMessage(true);
           }        
        } catch (Exception e) {
          AppLogger.logSevereError("REST request failed with exception: "+e.getMessage(), this.getClass().getSimpleName(), "updateIncidentStatus");
          this.setDcMessage("Update Failed. Unable to send the MCS REST request");
        }

    }

    
    /**
     * Queries image from MCS collection for the provided imageURL
     * 
     * @param incidentImageURL  URI for the image associated with an incident
     * @return base64 encoded image string
     */
    public String getIncidentImageFromMCS(String incidentImageURL){
        return FiFImageHandler.getImage(incidentImageURL, this.mFifMobileBackend);
    }
        
    /*
     * ANALYTICS
     */
   
   
   
    /**
     * Analytic events are aggregated and then send in a group by the application issuing a call to flushAnalyticEventsToServer()
     *
     * @param eventName a custom name that makes sense in the context of the application and that describes the task to log
     * @param eventProperties any set of custom key/value pairs that you want to keep track of
     */
    public void addCustomAnalyticEvent(String eventName, HashMap<String,String> eventProperties){        
            
            if(this.mFifEnableAnalytics == true && this.mFifAnalytics != null){                  
                Session analyticSession =  mFifAnalytics.getSession();
                if(analyticSession == null){
                    mFifAnalytics.startSession();
                }
                
                //create new event and associate it with the analytic session ID
                Event analyticEvent = new Event(eventName, new Date(), eventProperties, mFifAnalytics.getSession().getSessionId());  
                //queue this event for a later flush to the server
                mFifAnalytics.addEventToOutgoingQueue(analyticEvent);
            }
            else{
            FiFLogger.logFine("The analytics feature is disabled. Analytics enabled setting is = "+(this.mFifEnableAnalytics == true?"true":"false")+
                                  " Analytics object null? "+this.mFifAnalytics==null?"true":"false", this.getClass().getSimpleName(), "addAnalyticEvent");
            }
            
    }
    
    
    
    /**
     * publish all collected events to the server for the analytic engine to aggregate and save
     */
    public void flushAnalyticEventsToServer(){
        if(this.mFifEnableAnalytics == true && this.mFifAnalytics != null){                
            mFifAnalytics.flushEventQueueToServer();
        }
        else{
            FiFLogger.logFine("The analytics feature is disabled. Analytics enabled setting is = "+(this.mFifEnableAnalytics == true?"true":"false")+
                              " Analytics object null? "+this.mFifAnalytics==null?"true":"false", this.getClass().getSimpleName(), "sendAnalyticEventsToServer");
        }
    }
 
 
 
    /* ***********************************************
     *  Setter/Getter
     * *********************************************** */

    public void setTechnicianLocationLongLat(String technicianLocationLongLat) {
        String oldTechnicianLocationLongLat = this.technicianLocationLongLat;
        //override the input argument because the location is read from the device and 
        //not provided by the user imput
        this.technicianLocationLongLat = this._getCurrentGeoPosition();
        propertyChangeSupport.firePropertyChange("technicianLocationLongLat", oldTechnicianLocationLongLat,
                                                 technicianLocationLongLat);
    }

    public String getTechnicianLocationLongLat() {
        //ensure value upon initial get call
        if(this.technicianLocationLongLat == null){
            this.technicianLocationLongLat = this._getCurrentGeoPosition();
        }
        return technicianLocationLongLat;
    }


    public void setTechnicianLocationLong(String technicianLocationLong) {
                
        String oldTechnicianLocationLong = this.technicianLocationLong;
        
        //The location is determined by device access and not by usre input
        //" Lat/Long example: 39.355589,-120.652492
        String technicianLocation = this.getTechnicianLocationLongLat();
        
        this.technicianLocationLong = technicianLocation.substring(technicianLocation.indexOf(",")+1);
        propertyChangeSupport.firePropertyChange("technicianLocationLong", oldTechnicianLocationLong,
                                                 technicianLocationLong);
    }

    public String getTechnicianLocationLong() {
        
        //8nitial read provisioning
        if(this.technicianLocationLong == null){

            String technicianLocation = this.getTechnicianLocationLongLat();
            
            this.technicianLocationLong = technicianLocation.substring(technicianLocation.indexOf(",")+1);
        }
        
        return technicianLocationLong;
    }

    public void setTechnicianLocationLat(String technicianLocationLat) {

        String oldTechnicianLocationLat = this.technicianLocationLat;
        
        //The location is determined by device access and not by usre input
        //" Lat/Long example: 39.355589,-120.652492
        String technicianLocation = this.getTechnicianLocationLongLat();
        
        this.technicianLocationLat = technicianLocation.substring(0, technicianLocation.indexOf(","));
        
        this.technicianLocationLat = technicianLocationLat;
        
        propertyChangeSupport.firePropertyChange("technicianLocationLat", oldTechnicianLocationLat,
                                                 technicianLocationLat);
    }

    public String getTechnicianLocationLat() {
        
        if(this.technicianLocationLongLat == null){

            String technicianLocation = this.getTechnicianLocationLongLat();
            
            this.technicianLocationLat = technicianLocation.substring(0, technicianLocation.indexOf(","));

        }
        
        return technicianLocationLat;
    }

    /**
     * method implements property change notification to ensure that error messages that are displayed through the
     * data control in a view are refreshed.
     *
     * @param mDcErrorMessage
     */
    public void setDcMessage(String mDcErrorMessage) {
        String oldMDcErrorMessage = this.dcMessage;
        this.dcMessage = mDcErrorMessage;
        propertyChangeSupport.firePropertyChange("mDcErrorMessage", oldMDcErrorMessage, mDcErrorMessage);
    }
    
    
    public String getDcMessage() {
        return dcMessage;
    }


    public void setDcHasMessage(boolean mDcHasErrors) {
        boolean oldMDcHasErrors = this.dcHasMessage;
        this.dcHasMessage = mDcHasErrors;
        propertyChangeSupport.firePropertyChange("mDcHasErrors", oldMDcHasErrors, mDcHasErrors);
    }

    public boolean isDcHasMessage() {
        return dcHasMessage;
    }        
    
    
    /**
     * Queries an incident by the incident ID from MCS. This method depends on the availability of the current incident ID to 
     * be set prior to invoking this method. Note that the setter/getter approach was chosen for this access as it is easier to
     * build the detail page based on this. 
     * 
     * @see setCurrentIncidentId
     * 
     * @return List with a single incident queried from MCS. Note that the detail list is always queried from the MCS instance and
     * not read from the cahed objects. Reason for this is that the detail page is also accessed from push notification and there is 
     * no guarantee tha the data was fetched. Not to overcomplicate things for a demo, the decision was made to always query live data
     */    
    public List<Incident> getCurrentIncident(){      
        return this._getIncidentByIdFromMCS(this.getCurrentIncidentId());
    }
    
    /**
     * Queries a single Incident from MCS based on the incident id
     * @param incidentId A valid incident string
     * @return an instance of Incident or null (if no valid id is provided)
     */
    public Incident querySingleIncidentById(String incidentId){
        if(incidentId == null || incidentId.isEmpty()){
            AppLogger.logSevereError("Incident id in call querySingleIncidentById cannot be null", this.getClass().getSimpleName(), "querySingleIncidentById");                
            return null;
        }
        return _getSingleIncidentById(incidentId);
    }

    public void setCurrentIncidentId(String currentIncidentId) {
        String oldCurrentIncidentId = this.currentIncidentId;
        this.currentIncidentId = currentIncidentId;
        
        HashMap<String,String> customEventDef = new  HashMap<String,String>();
       
        customEventDef.put("incident Id", currentIncidentId);
        customEventDef.put("action","select incident");     
        customEventDef.put("mobile-backend-id",this.mFifConfig.getMobileBackendId());
        customEventDef.put("username",this.mFifConfig.getAuthenticatedUsername()==null?"Unauthenticated":this.mFifConfig.getAuthenticatedUsername());
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
        Date d = new Date();
        String timeStamp = sdf.format(d);
        
        customEventDef.put("timeStamp",timeStamp);            
        
        this.addCustomAnalyticEvent("DataControlEvent", customEventDef);
        
        propertyChangeSupport.firePropertyChange("currentIncidentId", oldCurrentIncidentId, currentIncidentId);
    }

    public String getCurrentIncidentId() {
        return currentIncidentId;
    }
        
    /**
     * Method is called from managed bean that performs the authentication in the process
     * of application execution. The username and token are saved in the MBE configuration
     * for later use
     * 
     * @param username
     * @param token
     */
    public void setOauthAuthenticatedUsernameAndToken(String username, String token){
                
        FiFLogger.logFine("Username and OAUTH token set : username = "+username+" token="+token, this.getClass().getSimpleName(), "setOauthAuthenticatedUsernameAndToken()");
        
        //the information needs to be set to the MBE configuration object so it becomes
        //available throughout the model
        this.mFifMobileBackend.getMbeConfiguration().setOauthHttpHeaderToken(token);
        this.mFifMobileBackend.getMbeConfiguration().setAuthenticatedUsername(username);
    }
    



    /* ***********************************************
     *  MCS NOTIFICATION REGISTRATION / DEREGISTRATION
     * *********************************************** */

    public void registerForMCSNotification(){
        
        //execute device registration asynchronously as there is no reason to wait
        Runnable DeviceRegistrationService = new Runnable(){
           public void run(){                
               
            //the token is saved in the FiFPushHandler called in the Application Controller project
            //example for debugging purposes: "c7645c692e143855054b40c3621d4c262ce1f97f0fd62a844bef34eab991758b"
            String tokenToRegister  = (String) AdfmfJavaUtilities.getELValue(FiFConstants.PUSH_TOKEN);              
            if(tokenToRegister == null){
                   FiFLogger.logWarning("Token not found. Please check log if it was received from the vendor", this.getClass().getSimpleName(), "registerForMCSNotification");
               }
               
               else {
                   
                   Notifications mcsNotificationRegistration = new Notifications(mFifMobileBackend);
                   mFifMobileBackend.getMbeConfiguration().setDeviceToken(tokenToRegister);
                   mcsNotificationRegistration.registerDeviceToMCSForPush();            
                   
               }
           }
         };                          
        
         ExecutorService executor = Executors.newFixedThreadPool(2);
         executor.execute(DeviceRegistrationService);                 
         executor.shutdown();
                         
    }
    
    
    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
    
    public void deRegisterForMCSNotification(){
        
        
        //execute device deregistration asynchronously as there is no reason to wait
        Runnable DeviceRegistrationService = new Runnable(){
           public void run(){                
               //the token is saved in the FiFPushHandler called in the Application Controller project
               //example for debugging purposes: "c7645c692e143855054b40c3621d4c262ce1f97f0fd62a844bef34eab991758b"
               String tokenToDeRegister  = (String) AdfmfJavaUtilities.getELValue(FiFConstants.PUSH_TOKEN);            
               FiFLogger.logFine("MCS Device De-Registration : token = "+tokenToDeRegister, this.getClass().getSimpleName(), "deRegisterForMCSNotification()");
                  
               if(tokenToDeRegister == null){
                   FiFLogger.logWarning("Token not found. Please check log if it was received from the vendor", this.getClass().getSimpleName(), "deregisterForMCSNotification");
               }
               else {
                   Notifications mcsNotificationRegistration = new Notifications(mFifMobileBackend);
                   
                   mFifMobileBackend.getMbeConfiguration().setDeviceToken(tokenToDeRegister);
                   mcsNotificationRegistration.deregisterDeviceToMCSForPush();  
                   
                   
               }
           }
         };                          
        
         ExecutorService executor = Executors.newFixedThreadPool(2);
         executor.execute(DeviceRegistrationService);                 
         executor.shutdown();
    }


    /* ***********************************************
     *  PROVIDER CHANGE SUPPORT
     * *********************************************** */
    
    public void addProviderChangeListener(ProviderChangeListener l) {
        providerChangeSupport.addProviderChangeListener(l);
    }

      public void removeProviderChangeListener(ProviderChangeListener l) {
         providerChangeSupport.removeProviderChangeListener(l);
    }
         
         
         

    /* ***********************************************
     *  PRIVATE METHODS 
     * *********************************************** */
      
      
    /**
     * Queries a single Incident by the ID. Note that detail forms are easy to create in MAF if the content is saved in a
     * collection. Therefore this query does not return a single object but a collection containing the single object.
     *
     * @param incidentId the ID of the incident report
     * @return List of Incident with a single or no entry
     */
     private List<Incident> _getIncidentByIdFromMCS(String incidentId){
                         
         if(incidentId == null || incidentId.isEmpty()){
             AppLogger.logSevereError("Incident id in call to _getIncidentByIdFromMCS cannot be null", this.getClass().getSimpleName(), "_getIncidentByIdFromMCS");                
             //return empty list
             return new ArrayList<Incident>();
         }
                  
         Incident incident = _getSingleIncidentById(incidentId);         
         ArrayList<Incident> incidentList = new ArrayList<Incident>();
         incidentList.add(incident);
         return incidentList;
     }

    /**
     * Queries a single Incident object from MCS by the incident ID
     * @param incidentId A vaid incident number
     * @return Incident or null if no incident is found in MCS
     */
    private Incident _getSingleIncidentById(String incidentId) {
        
        String queryURI = FIF_CUSTOM_API_INCIDENTS_URI+"/"+incidentId;
        
        //request context sets header application/json by default and also add empty payload
        RequestContext request = new RequestContext();
        request.setConnectionName(REST_CONNECTION_NAME);
        request.setHttpMethod(RequestContext.HttpMethod.GET);
        request.setRequestURI(queryURI);
        
        HashMap<String, String> httpHeaders = new HashMap<String, String>();
        httpHeaders.put("oracle-mobile-backend-id", this.mFifMobileBackend.getMbeConfiguration().getMobileBackendId());
        httpHeaders.put("Authorization", this.mFifMobileBackend.getMbeConfiguration().getOauthHttpHeaderToken());
        
        request.setHttpHeaders(httpHeaders);
        Incident incident = this._querySingleIncidentFromMCS(request);
        return incident;
    }  
    
    /**
     * 
     * @param request
     * @return Incident or null if incident cannot be found or if an error occurs
     */
    private Incident _querySingleIncidentFromMCS(RequestContext request) {
        
        resetDataControlMessages();
        
        AppLogger.logFine("URI for querying incident reports: "+request.getRequestURI(), this.getClass().getSimpleName(), "_querySingleIncidentFromMCS");

        try {
            ResponseContext response = RestClient.sendForStringResponse(request);
            AppLogger.logFine("REST response: http-"+response.getResponseStatus()+" "+response.getResponsePayload() , this.getClass().getSimpleName(), "_querySingleIncidentFromMCS");
            //its a GET request and we expect http status 200 to indicate success
            if(response.getResponseStatus() == ResponseContext.STATUS_RESPONSE_OK){
                
               String jsonResult = (String) response.getResponsePayload();  
               //parse JSON result into JSONObject
               JSONObject incidentJSONObject = new JSONObject(jsonResult);
               
               Incident singleIncident = new Incident();  
               singleIncident.populateInstanceFromJSON(incidentJSONObject);
               
               return singleIncident;

            }
            else{
                AppLogger.logSevereError("REST request succeeded but then failed with error code: "+response.getResponseStatus(), this.getClass().getSimpleName(), "_querySingleIncidentFromMCS");                
                AppLogger.logSevereError("Error message: "+response.getResponsePayload(), this.getClass().getSimpleName(), "_querySingleIncidentFromMCS");                
                
                this.setDcMessage("Request failed with error: "+response.getResponsePayload());
                this.setDcHasMessage(true);
            }
            
        } catch (Exception e) {
            AppLogger.logSevereError("REST request failed with exception: "+e.getMessage(), this.getClass().getSimpleName(), "_querySingleIncidentFromMCS");
            this.setDcMessage("Request failed. Unable to send REST MCS request.");
            this.setDcHasMessage(true);
        }        
        return null;
    }
    
    
    /**
     * Configure the mobile backend instance used by the FIF Techniciab application by reading configuration information 
     * from the MAF applicationpreferences
     */
    private void _initMobileBackend() {
        
        
        //setup the MBE instance
        FiFLogger.logFine("Creating FiF Mobile Backend Configuration", this.getClass().getSimpleName(), "initMobileBackend");
        
        mFifConfig = new FiFMBEConfig("FIFMBE",this.mFifMobileBackendId,this.mFifMobileBackendApplicationKey); 
        mFifConfig.setMafRestConnectionName("FiFMBE");
        
        mFifConfig.setMobileBackendApplicationKey(this.mFifMobileBackendApplicationKey);
        
        mFifConfig.setMobileBackendId(mFifMobileBackendId);
        
        FiFLogger.logFine("Creating FiF Mobile Bakend Instance", this.getClass().getSimpleName(), "initMobileBackend");
        mFifMobileBackend = new FiFMobileBackend(this.mFifMobileBackendName,mFifConfig);
        
        //create MCS interaction points
        FiFLogger.logFine("Start initializing MCS interaction handlers", this.getClass().getSimpleName(), "initMobileBackend");
        
        mFifAnalytics = new Analytics(mFifMobileBackend);
        mFifMobileBackend.setApplicationFeatureName(AdfmfJavaUtilities.getFeatureName());
        FiFLogger.logFine("End initializing MCS interaction handlers", this.getClass().getSimpleName(), "initMobileBackend");
     
    }
    
    /**
     * Reads Mobile Backend configuration information from MAF appication preferences
     * @return false if initialization fails with errors for required proerties. True otherwise
     */
    private boolean _initFormAplicationPreferences(){
        
        resetDataControlMessages();
        
        ArrayList<String> errors = new ArrayList<String>();
        
        //fifMobileBackendId - required
        mFifMobileBackendId = (String) AdfmfJavaUtilities.getELValue("#{preferenceScope.application.mcs.fifMobileBackendId}");
        if(mFifMobileBackendId == null){
            errors.add("Mobile Backend ID");
        }

        FiFLogger.logFine("Mobile Backend ID: "+mFifMobileBackendId, this.getClass().getSimpleName(), "initFromPreferences()");
                
        //fifMobileBackendName - required
        mFifMobileBackendName = (String) AdfmfJavaUtilities.getELValue("#{preferenceScope.application.mcs.fifMobileBackendName}");
        if(mFifMobileBackendName == null){
            errors.add("FIF Mobile Backend Name");
        }
        FiFLogger.logFine("FIF Mobile Backend Name: "+mFifMobileBackendName, this.getClass().getSimpleName(), "initFromPreferences()");
                       
        //The application key challenge is that MAF applications are registered twice with a Mobile Backend in MCS: one time for 
        //iOS and one time for Android. This sample requires both keys to be provided in the preferences and then determines the 
        //key to use based on the OS the application runs on
        
        //fifMobileBackendApplicationKey - required
        String androidAppKey = (String) AdfmfJavaUtilities.getELValue("#{preferenceScope.application.mcs.fifMobileBackendApplicationKeyAndroid}");
        FiFLogger.logFine("FiF Mobile Backend Application Key for Android: "+androidAppKey, this.getClass().getSimpleName(), "initFromPreferences()");
        
        String iOSAppKey = (String) AdfmfJavaUtilities.getELValue("#{preferenceScope.application.mcs.fifMobileBackendApplicationKeyiOS}");
        FiFLogger.logFine("FiF Mobile Backend Application Key for iOS: "+iOSAppKey, this.getClass().getSimpleName(), "initFromPreferences()");
        
        //next check the platform MAF runs on
        String mobileOs = DeviceManagerFactory.getDeviceManager().getOs();
        
        if(mobileOs.toUpperCase().contains("IOS") ){
            mFifMobileBackendApplicationKey = iOSAppKey;
            if(iOSAppKey == null){
                errors.add("FiF Mobile Backend Application Key (iOS) is Missing");
            }
        }
        else{
            mFifMobileBackendApplicationKey = androidAppKey;
            if(androidAppKey == null){
                errors.add("FiF Mobile Backend Application Key (Android) is missing");
            }
        }
        
        if(!errors.isEmpty()){
            StringBuffer messageBuffer = new StringBuffer();
            messageBuffer.append("The following application preference settings are mandatory and cannot be NULL: ");
            for(String error : errors){
                messageBuffer.append("\""+error+"\" ");
                FiFLogger.logSevereError(messageBuffer.toString(), this.getClass().getSimpleName(), "initFromPreferences()");
                //set error message for display on DC
                this.setDcMessage(messageBuffer.toString());
                this.setDcHasMessage(true);
            }
            
            return false;
        }
        
        return true;
        
    }
    
    /**
    * Queries the incidents in MCS by filter of technician and (optionally) gps (Latitide, Longitude)). 
    * 
    * @param technician    Name of the assigned technician
    */
    private void _qryIncidentsFromMcsForTechnician(String technician){
        
        if(technician == null){
            throw new AdfException("The name of the technician cannot be null",AdfException.ERROR);
        }
        
 /*                               
        Runnable McsIcidentDataFetcher = new Runnable(){
           public void run(){  
               
               allIncidents = new ArrayList<Incident>(_getIncidentsFromMcsWithFilter(null, technician, _getCurrentGeoPosition()));
               
               //save a copy
               mIncidentListCache = new ArrayList<Incident>();
               //create copy of incident array in cache
               mIncidentListCache.addAll(new ArrayList<Incident>(allIncidents));
               
               AppLogger.logFine("Asynchronous fetching of remote data: "+allIncidents.size()+" items found", this.getClass().getSimpleName(), "executeFilteredIncidentsQuery");
               
               //refresh the UI with the change
               providerChangeSupport.fireProviderRefresh("allIncidents");
               //refresh is from thread. Thus we need to call flushDataChangeEvent()
               AdfmfJavaUtilities.flushDataChangeEvent();
           }
         };                          
        
         ExecutorService executor = Executors.newFixedThreadPool(2);
         executor.execute(McsIcidentDataFetcher);                 
         executor.shutdown();    
*/         
         
        allIncidents = new ArrayList<Incident>(_getIncidentsFromMcsWithFilter(null, technician, _getCurrentGeoPosition()));
        
        //save a copy
        mIncidentListCache = new ArrayList<Incident>();
        //create copy of incident array in cache
        mIncidentListCache.addAll(new ArrayList<Incident>(allIncidents));
        
        AppLogger.logFine("Asynchronous fetching of remote data: "+allIncidents.size()+" items found", this.getClass().getSimpleName(), "executeFilteredIncidentsQuery");
        
        //refresh the UI with the change
        providerChangeSupport.fireProviderRefresh("allIncidents");
        //refresh is from thread. Thus we need to call flushDataChangeEvent()
        AdfmfJavaUtilities.flushDataChangeEvent();         
    }
    
           
    /**
     * /**
     * Queries the allIncidents in MCS by filter of contact (incident reportee) and technician. Note that one of the "contact" or "technician"
     * parameter MUST be included in the call as otherwise the REST call fails and an empty incident list is returned. The GPS coordinates are
     * used to tell the difference between the location of the technician (based on the app) and the contact who filed the incident report
     *
     * @param contact Name of the incident report contact person
     * @param technician username of the technician
     * @param gps Latitude, Longitude, separated by comma  E.g. 39.355589,-120.652492
     * @return An array of Incident objects or an empty array
     */
    private ArrayList<Incident> _getIncidentsFromMcsWithFilter(String contact, String technician, String gps){
        
        if((contact == null || contact.isEmpty()) && (technician == null || technician.isEmpty())){
            throw new AdfException("Contact and Technician name cannot both be empty or null",AdfException.ERROR);
        }
        
        ArrayList<Incident> _incidentList = new ArrayList<Incident>();        
        
        String queryParams = null;
        
        if(contact != null && contact.length()>0){
            queryParams = "?contacts="+contact;
        }
        
        if(technician != null && technician.length()>0){
            if(queryParams == null){
                queryParams = "?technician="+technician;
            }
            else{
                queryParams = queryParams+"&technician="+technician;
            }
        }
        
        if(gps != null && gps.length()>0){
            if(queryParams == null){                
                AppLogger.logSevereError("One argument out of \" contact\" or \"technician\" must have a value provided", this.getClass().getSimpleName(), "executeFilteredIncidentsQuery");
                //returning empty list as no query should be executed against the remte REST client
                return _incidentList;
            }
            else{
                queryParams = queryParams+"&gps="+gps;
            }
        }
                 
        String fullRequestURI = FIF_CUSTOM_API_INCIDENTS_URI+queryParams;   
        
        
        //request context sets header application/json by default and also 
        //add empty payload
        RequestContext request = new RequestContext();
        request.setConnectionName(REST_CONNECTION_NAME);
        request.setHttpMethod(RequestContext.HttpMethod.GET);
        request.setRequestURI(fullRequestURI);
        request.setRetryLimit(2);
        
        HashMap<String, String> httpHeaders = new HashMap<String, String>();
        httpHeaders.put("oracle-mobile-backend-id", this.mFifMobileBackend.getMbeConfiguration().getMobileBackendId());
        httpHeaders.put("Authorization", this.mFifMobileBackend.getMbeConfiguration().getOauthHttpHeaderToken());

        request.setHttpHeaders(httpHeaders);
        
        _incidentList = _getIncidentsFromMCS(request);
        
        return _incidentList;    
    }

    /**
     * Query incident lists based on the filter definition in the RequestContext object
     * 
     * @param request  Instance of RequestContext
     */
    private ArrayList<Incident> _getIncidentsFromMCS(RequestContext request) {

        resetDataControlMessages();
        
        ArrayList<Incident>  incidentList = new ArrayList<Incident>();

        
        AppLogger.logFine("URI for querying incident reports: "+request.getRequestURI(), this.getClass().getSimpleName(), "executeFilteredIncidentsQuery");

        try {
            ResponseContext response = RestClient.sendForStringResponse(request);
            
            //its a GET request and we expect http status 200 to indicate success
            if(response.getResponseStatus() == ResponseContext.STATUS_RESPONSE_OK){
                
               String jsonResult = (String) response.getResponsePayload();  
               //parse JSON result into JSONArray
               Incidents incidentsQueriedFromMCS = (Incidents) JSONBeanSerializationHelper.fromJSON(Incidents.class, jsonResult);
               //the Incidents class handles the JSONObject to entity conversion
               incidentList = incidentsQueriedFromMCS.populateIncidentList();
               
                this.setDcMessage("Update Sucessful.");
                this.setDcHasMessage(true);
                
            }
            else{
                AppLogger.logSevereError("REST request succeeded but then failed with error code: "+response.getResponseStatus(), this.getClass().getSimpleName(), "executeFilteredIncidentsQuery");                
                AppLogger.logSevereError("Error message: "+response.getResponsePayload(), this.getClass().getSimpleName(), "executeFilteredIncidentsQuery");                
                this.setDcMessage("Request failed with errors:" +response.getResponsePayload());
                this.setDcHasMessage(true);
                
            }
            
        } catch (Exception e) {
            AppLogger.logSevereError("REST request failed with exception: "+e.getMessage(), this.getClass().getSimpleName(), "executeFilteredIncidentsQuery");
            this.setDcMessage("Request failed. Unable to send MCS Service Request");
            this.setDcHasMessage(true);    
        }        
        return incidentList;
    }
     
    /**
     * Obtain the current GEO position 
     * @return Latitude, Longitude, separated by comma  E.g. 39.355589,-120.652492
     */
    private String _getCurrentGeoPosition(){
        boolean supportsGeoLocation  = false;
        
        //allow location information to be up to 1 minutes old
        int maxAllowedAgeofCachedLocationData = 60;
        
        //position doesn't need to be highly accurate for analytics
        boolean enableHighAccuracy = false;
        
        
        supportsGeoLocation = DeviceManagerFactory.getDeviceManager().hasGeolocation();
        
        FiFLogger.logFine("Is Geo location enabled? : "+supportsGeoLocation , this.getClass().getSimpleName(), "initFromPreferences()");
        
        if(supportsGeoLocation){
            //on simulators you may not be able to access the GEO location. To avoid errors, we simply set a 
            //static long/lat value
            try {
                Location location =
                    DeviceManagerFactory.getDeviceManager().getCurrentPosition(maxAllowedAgeofCachedLocationData,
                                                                               enableHighAccuracy);
                double latitude = location.getLatitude();
                double longitude = location.getLongitude();

                FiFLogger.logFine("Geo location is /LAT/LONG) : " + Double.toString(latitude) + "," +
                                  Double.toString(longitude), this.getClass().getSimpleName(),
                                  "_getCurrentGeoPosition()");
                return Double.toString(latitude) + "," + Double.toString(longitude);
                
            } catch (Exception e) {
                
                AppLogger.logWarning("DeviceManagerFactory.getDeviceManager().getCurrentPosition --> Problem getting current Position", this.getClass().getSimpleName(), "_getCurrentGeoPosition");
                AppLogger.logWarning("Excepion is: "+e.getLocalizedMessage(), this.getClass().getSimpleName(), "_getCurrentGeoPosition");
                AppLogger.logWarning( "Setting a fixed location as: 39.355589,-120.652492", this.getClass().getSimpleName(), "_getCurrentGeoPosition");
               
                return "39.355589,-120.652492"; 
            }
        }
        else{
            //we will put you into a fixed location
            AppLogger.logWarning("DeviceManagerFactory.getDeviceManager().hasGeolocation() --> This device does not support GPS geo-localisation. Setting a fixed location as: 39.355589,-120.652492", this.getClass().getSimpleName(), "_getCurrentGeoPosition");
            return "39.355589,-120.652492";            
        }
        
    }
    
    public void resetQueriedIncidentData(){
    
        //deleting the cache list leads to a re-fetch of the 
        //data incident data from MCS
        this.mIncidentListCache = null;
        
        //refetch data from MCS
        this.getAllIncidents();
        
        //refresh UI
        providerChangeSupport.fireProviderRefresh("allIncidents");
        
    }
    
    public void clearCacheIncidentData(){
    
        //deleting the cache list leads to a re-fetch of the 
        //data incident data from MCS
        this.mIncidentListCache = null;   
        this.allIncidents = null;
    }

    public void resetDataControlMessages(){
        this.setDcHasMessage(false);
        this.setDcMessage("");
    }

    public void addPropertyChangeListener(PropertyChangeListener l) {
        propertyChangeSupport.addPropertyChangeListener(l);
    }

    public void removePropertyChangeListener(PropertyChangeListener l) {
        propertyChangeSupport.removePropertyChangeListener(l);
    }
}
