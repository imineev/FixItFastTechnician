Fix-It-Fast Technician Quick-Introduction
=========================================

FiFItFastTechnician-MAF-2.2.1-MCS-15.3.5 : Tested  on MCS v1.0 (15.3.5)  with MAF extension 2.2.1

The FiF Technician application requires the FIF_TECHNICIAN 1.0 Mobile Backend (MBE) in Oracle MCS. 
The MBE settings panel lists the values to copy&paste into the FiF Technician application preferences that  
are accessible from the maf-application.xml in Oracle JDeveloper or in the iOS or Android preference panel
on the device. 

Note that the FiF Technician application is built against a MCS instance that uses Basic HTTP  for authentication. 

MCS instances used are Internet facing (OPC). No need of using Oracle Cisco VPN on device.

Take care to enable Location service on the device before demoing

Preferences:
============
fifMobileBackendURL 			==> MBE Base URL
fifMobileBackendName			==> FIF Mobile Backend Name : FIF_TECHNICIAN
fifMobileBackendId			==> Mobile Backend ID
fifMobileBackendApplicationKeyAndroid	==> Application Key of the Android client Application in the MBE
fifMobileBackendApplicationKeyiOS 		==> Application Key of the iOS client Application in the MBE
fifMBEAnonymousKey			==> Anonymous Key of the MBE
gcmSenderId				==> Google GMC Sender ID for the Android registered client application in the MBE
appleBundleId				==> Apple Application Id for the iOS client Application in the MBE
googleApplicationPackage			==> Google Application Id for the Android client Application in the MBE
enablePush				==> Enable Receiving Push Notifications
pushMessagesForDebug			==> Shows Push Raw Messages in a debug panel. Can be enable at runtime

How-to demo
============

1. Authenticate as joe/Welcome2!* or jill/Welcome2!*
   -> After authentication, the application registers with Apple APNS or Android GMC to receive push notifications from MCS
   -> Also after authentication, the application starts collecting Analytic events
2. In the initial SR List view, select an incident to navigate to the detail page
   -> The list shows data queried from a MCS custom API
   -> Selecting a filter criteria and pressing the filter icon allows for in-memory filtering
3. On the detail page show the MAP, switch to the image and then to the notes panel
   -> The image is fetched asynchronously from the MC Storage (user isolated)
4. On the notes panel, add a comment and change the status of the incident. Press the OK icon to submit the note
   -> Submitting the note sends an update push message to the customer
5. Tab on the "Back" button in the upper left corner to navigate back to the list page
  -> on navigating back, the collected analytic events are flushed to the MCS server
6. In MCS, select FIF_TECHNICIAN 1.0 MBE and tap onto the "Open" button. Switch to the Notifications panel using the 
   left side menu
7. In the notification dialog, add a message with the following structure
      {id:"113",title: "Update:House Destroyed After Earthquake",notes:"Need someone to fix the roof"}
    -> ensure the id value (113 in the example) exists in MCS as otherwise navigation will fail
8. Press the send button
9. In the application, a panel should display with the notification message displayed (Note that on Android you may need to 
   click on the device alert that shows when the message is received to refresh the screen)
10. Press the "Show Me" link to display the detail information that belongs to the notification incident Id
11. Press "Back" again to navigate back to the list view
12. Optional: Requery the list of incidents pressing the reload icon on the top right
     -> see: known issues (2) below
13. Switch to MCS MBE and show Analytics. You should see analytic events like "DataControlEvent", "incident", "view-navigation" etc. 
	-> Ensure FIF_TECHNICIAN is selected in the Analytic panel (otherwise you may not see information)
14. Tab the "Exit"	icon to de-register from push and to show the login screen again


Known issues: 
===============

1- The Oracle Cisco VPN access may not pass notifications through. This is especially the case for Android devices. Here you may want to 
   disconnect from VPN while showing the SR List view to see notifications 
   ==> You have no need to be connected VPN as MCS instances used are Internet facing (OPC)

2- Even if a technician updated an incident sent through notification, he/she may not see it in the queried list of incidents . This is because the MCS custom API 
   does assign a technician to each request independently of sending a push to everyone. Best therefore is to use an ID of an incident the authenticated technician 
   can see when testing push
   
  
What's provided
==================

1. ipa file		=> you will have to go through the  Preferences page in the appp on the device before to be able to use it.
2. apk file	=> you will have to go through the  Preferences page in the appp on the device before to be able to use it
3. JDeveloper workspace

What is not provided
====================

For legal and licensing reason, the following information is not shipped with the sample

1. certificate to compile and sign application 
2. p12 certificate to setup iOS client application in MCS
3. Google GCM sender Id and project key

If you need one of these, please get in touch with chris.muir@oracle.com to help you configuring your MBE instance. This however is only possible
for Oracle employees. 