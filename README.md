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
  <li><a href="http://developer.android.com/sdk/index.html">Android SDK</a>. In the <a href="http://developer.android.com/tools/help/sdk-manager.html">Android SDK Manager</a> you need to install at least the <i>Android SDK Tools</i>, the <i>Android SDK Platform-tools</i>, and <i>Android 4.4.2 (API 19)</i>. For the API 19 the following items are mandatory:
    <ul>
      <li>SDK Plattform</li>
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
      <li>You need the <a href="https://github.com/mosabua/maven-android-sdk-deployer">maven-android-sdk-deployer</a>. Follow the instructions there. Only platform 4.4 needs to be installed.</li>
      <li>You also need the Android Library for the ACR122u, even if you do not plan to use it in your application. You can download the .jar on the <a href="http://www.acs.com.hk/download-driver/2989/ACS-Unified-LIB-Android-111-P.zip">ACS website</a> or from this git repository <a href="https://github.com/jetonmemeti/android-kitkat-nfc-library/blob/develop-tb/acssmc-1.1.1.jar">here</a>.</li>
    </ul>
  </li>
  <li>Clone this git repository.</li>
  <li>In Eclipse go to <i>File --> Import --> Maven --> Existing Maven Project</i>. In the appearing dialog under <i>Root Directory</i> enter the path to the checked out project in your git repository.</li>
  <li>Select the project <i>AndroidKitKatNFCLibrary</i> and click on <i>Finish</i>.</li>
  <li>Run mvn install on this project to install it to your local maven repository. Now you can use this library in your project by adding its <i>groupId</i>, <i>artifactId</i>, and <i>version</i> (see pom.xml) to the POM of your project.</li>
</ul>
How to Use:
-----------
You can have a look at or check out the <a href="https://github.com/jetonmemeti/android-nfc-payment-library">AndroidNFCPaymentLibrary</a> to see how you can use this library.
