android-kitkat-nfc-library
==========================

This is an Android library which offers NFC peer-to-peer functionality for Android KitKat devices irrespective of the build-in NFC controller (Broadcom or NXP).<br>
In addition to the Android NFC functionality, this library allows sending larger messages by providing message fragmentation and reassembly. It also makes it possible to use the two-way NFC (<a href="https://developer.android.com/guide/topics/connectivity/nfc/hce.html">Host-based Card Emulation</a>) with NXP devices, since it covers connection disruptions appropriately.<br>
Furthermore, it also supports the <a href="http://www.acs.com.hk/en/products/3/acr122u-usb-nfc-reader/">ACR122u USB NFC Reader</a>, which can be attached to an Android KitKat device.

Prerequisites:
--------------
<ul>
  <li><a href="http://www.oracle.com/technetwork/java/javase/downloads/index.html">Java JDK 7</a></li>
  <li><a href="http://www.eclipse.org/downloads/">Eclipse 4.3</a></li>
  <li><a href="http://maven.apache.org/download.cgi">Maven 3.2.1</a></li>
  <li><a href="http://developer.android.com/sdk/index.html">Android SDK</a>. In the <a href="http://developer.android.com/tools/help/sdk-manager.html">Android SDK Manager</a> you need to install at least the <i>Android SDK Tools</i>, the <i>Android SDK Platform-tools</i>, and <i>Android 4.4.2 (API 19)</i>. For the API 19 the following items are mandatory:
    <ul>
      <li>SDK Platform</li>
      <li>Google APIs</li>
      <li>Glass Development Kit Preview</li>
    </ul>
  </li>
  <li>Having set up Eclipse according to this <a href="http://developer.android.com/sdk/installing/index.html">guideline</a>.</li>
</ul>

Installation Guidelines:
------------------------
<ul>
  <li>
    Install two third-party libraries to your local maven repository (read <a href="http://maven.apache.org/guides/mini/guide-3rd-party-jars-local.html">here</a> how to install 3rd party JARs).
    <ul>
      <li>You need the <a href="https://github.com/mosabua/maven-android-sdk-deployer">maven-android-sdk-deployer</a>. Follow the instructions there - only platform 4.4 needs to be installed. (This library requires the <a href="http://search.maven.org/#artifactdetails%7Corg.codehaus.mojo%7Cproperties-maven-plugin%7C1.0-alpha-2%7Cmaven-plugin">properties-maven-plugin</a> to be installed to your local maven repository. Otherwise, the installation of maven-android-sdk-deployer will fail.)</li>
      <li>You also need the Android Library for the ACR122u, even if you do not plan to use it in your application. You can download the JAR on the <a href="http://www.acs.com.hk/download-driver/2989/ACS-Unified-LIB-Android-111-P.zip">ACS website</a> or from this git repository <a href="https://github.com/jetonmemeti/android-kitkat-nfc-library/blob/develop-tb/acssmc-1.1.1.jar">here</a>.</li>
    </ul>
  </li>
  <li>Clone this git repository.</li>
  <li>In Eclipse go to <code>File --> Import --> Maven --> Existing Maven Project</code>. In the appearing dialog under <code>Root Directory</code> enter the path to the checked out project in your git repository.</li>
  <li>Select the project <i>AndroidKitKatNFCLibrary</i> and click on <code>Finish</code>.</li>
  <li>Run <code>mvn install</code> on the root folder of this project to install it to your local maven repository. Now you can use this library in your project by adding its <i>groupId</i>, <i>artifactId</i>, and <i>version</i> (see pom.xml) to the POM of your project.</li>
</ul>
How to Use:
-----------
You can have a look at or check out the <a href="https://github.com/jetonmemeti/android-nfc-payment-library">AndroidNFCPaymentLibrary</a> to see how you can use this library.

If you want to import this library into your own Android project - without using the <a href="https://github.com/jetonmemeti/android-nfc-payment-library">AndroidNFCPaymentLibrary</a> - there are three important things that you need to add to your project in order for the NFC to work properly. (See for example the <a href="https://github.com/jetonmemeti/SamplePaymentProject">SamplePaymentProject</a>.)
<ul>
  <li>Copy the file <a href="https://github.com/jetonmemeti/android-nfc-payment-library/blob/develop-tb/res/apduservice.xml">apduservice.xml</a> to <code>&lt;project root folder&gt;\res\xml\</code>.</li>
  <li>In <code>&lt;project root folder&gt;\res\values\strings.xml</code> add the following:<br>
    <pre><code>&lt;!-- APDU SERVICE --&gt;</code><br>
    <code>&lt;string name="aiddescription"&gt;ch.uzh.csg.nfclib&lt;/string&gt;</code><br>
    <code>&lt;string name="servicedesc"&gt;Android KitKat NFC Library&lt;/string&gt;</code></pre>
  </li>
  <li>In the <code>AndroidManifest.xml</code> add the following:<br>
    <pre><code>&lt;uses-sdk android:minSdkVersion="19" android:targetSdkVersion="19" /&gt;</code><br>  
    <code>&lt;uses-feature android:name="android.hardware.nfc" android:required="true" /&gt;</code><br>  
    <code>&lt;uses-permission android:name="android.permission.NFC" /&gt;</code></pre>
    Inside the <code>&lt;application&gt;</code> tag add:<br>
    <pre><code>&lt;service android:name="ch.uzh.csg.nfclib.CustomHostApduService" android:exported="true" android:permission="android.permission.BIND_NFC_SERVICE"&gt;<br>
  &lt;intent-filter&gt;<br>
    &lt;action android:name="android.nfc.cardemulation.action.HOST_APDU_SERVICE" /&gt;<br>
  &lt;/intent-filter&gt;<br>
  &lt;meta-data android:name="android.nfc.cardemulation.host_apdu_service" android:resource="@xml/apduservice" /&gt;<br>
&lt;/service&gt;</code></pre>
  </li>
</ul>

Once this is done, you can use this library in your project by adding its <i>groupId</i>, <i>artifactId</i>, and <i>version</i> (see pom.xml) to the POM of your project.
