import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;

public class Deployer {

    private static String codeTimeId = "com.softwareco.intellij.plugin";
    private static String musicTimeId = "com.softwareco.intellij.plugin.musictime";

    private static String codeTimeImpl = "com.softwareco.intellij.plugin.SoftwareCo";
    private static String musicTimeImpl = "com.softwareco.intellij.plugin.SoftwareCoMusic";

    private static String codeTimeName = "Code Time";
    private static String musicTimeName = "Music Time";

    private static String codeTimeVersion = "0.4.4";
    private static String musicTimeVersion = "1.0.0";

    private static String codeTimeDesc = "\n" +
            "<h1 id=\"codetimeforintellij\">Code Time for IntelliJ</h1>\n\n" +

            "<blockquote>\n" +
            "<p><strong>Code Time</strong> is an open source plugin that provides programming metrics right in your code editor.</p>\n" +
            "</blockquote>\n\n" +

            "<p align=\"center\" style=\"margin: 0 10%\">\n" +
            "<img src=\"https://raw.githubusercontent.com/swdotcom/swdc-intellij/master/resources/assets/intellij-dashboard.gif\" alt=\"Code Time for IntelliJ\" />\n" +
            "</p>\n\n" +

            "<h2 id=\"powerupyourdevelopment\">Power up your development</h2>\n\n" +

            "<p><strong>In-editor dashboard</strong>\n" +
            "Get daily and weekly reports of your programming activity right in your code editor.</p>\n\n" +

            "<p><strong>Status bar metrics</strong>\n" +
            "After installing our plugin, your status bar will show real-time metrics about time coded per day.</p>\n\n" +

            "<p><strong>Weekly email reports</strong>\n" +
            "Get a weekly report delivered right to your email inbox.</p>\n\n" +

            "<p><strong>Data visualizations</strong>\n" +
            "Go to our web app to get simple data visualizations, such as a rolling heatmap of your best programming times by hour of the day.</p>\n" +

            "<p><strong>Calendar integration</strong>\n" +
            "Integrate with Google Calendar to automatically set calendar events to protect your best programming times from meetings and interrupts.</p>\n\n" +

            "<p><strong>More stats</strong>\n" +
            "See your best time for coding and the speed, frequency, and top files across your commits.</p>\n\n" +

            "<h2 id=\"whyyoushouldtryitout\">Why you should try it out</h2>\n\n" +

            "<ul>\n" +
            "<li>Automatic time reports by project</li>\n\n" +

            "<li>See what time you code your best-find your \"flow\"</li>\n\n" +

            "<li>Defend your best code times against meetings and interrupts</li>\n\n" +

            "<li>Find out what you can learn from your data</li>\n" +
            "</ul>\n\n" +

            "<h2 id=\"itssafesecureandfree\">It's safe, secure, and free</h2>\n\n" +

            "<p><strong>We never access your code</strong>\n" +
            "We do not process, send, or store your proprietary code. We only provide metrics about programming, and we make it easy to see the data we collect.</p>\n\n" +

            "<p><strong>Your data is private</strong>\n" +
            "We will never share your individually identifiable data with your boss. In the future, we will roll up data into groups and teams but we will keep your data anonymized.</p>\n\n" +

            "<p><strong>Free for you, forever</strong>\n" +
            "We provide 90 days of data history for free, forever. In the future, we will provide premium plans for advanced features and historical data access.</p>\n";

    private static String musicTimeDesc = "\n" +
            "<h1 id=\"musictimeforintellij\">Music Time for IntelliJ</h1>\n\n" +

            "<blockquote>\n" +
            "<p><strong>Music Time</strong> is an open source plugin that provides music listening metrics right in your code editor.</p>\n" +
            "</blockquote>\n\n" +

            "<p align=\"center\" style=\"margin: 0 10%\">\n" +
            "<img src=\"https://raw.githubusercontent.com/swdotcom/swdc-intellij/master/resources/assets/intellij-dashboard.gif\" alt=\"Code Time for IntelliJ\" />\n" +
            "</p>\n\n" +

            "<h2 id=\"powerupyourdevelopment\">Power up your development</h2>\n\n" +

            "<p><strong>In-editor dashboard</strong>\n" +
            "Get daily and weekly reports of your music listening activity right in your code editor.</p>\n\n" +

            "<p><strong>Status bar metrics</strong>\n" +
            "After installing our plugin, your status bar will show real-time metrics about time music listen per day.</p>\n\n" +

            "<p><strong>Weekly email reports</strong>\n" +
            "Get a weekly report delivered right to your email inbox.</p>\n\n" +

            "<p><strong>Data visualizations</strong>\n" +
            "Go to our web app to get simple data visualizations, such as a rolling heatmap of your best programming times by hour of the day.</p>\n" +

            "<p><strong>Calendar integration</strong>\n" +
            "Integrate with Google Calendar to automatically set calendar events to protect your best programming times from meetings and interrupts.</p>\n\n" +

            "<p><strong>More stats</strong>\n" +
            "See your best time for coding and the speed, frequency, and top files across your commits.</p>\n\n" +

            "<h2 id=\"whyyoushouldtryitout\">Why you should try it out</h2>\n\n" +

            "<ul>\n" +
            "<li>Automatic time reports by project</li>\n\n" +

            "<li>See what time you listen music your best-find your \"flow\"</li>\n\n" +

            "<li>Defend your best music times against meetings and interrupts</li>\n\n" +

            "<li>Find out what you can learn from your data</li>\n" +
            "</ul>\n\n" +

            "<h2 id=\"itssafesecureandfree\">It's safe, secure, and free</h2>\n\n" +

            "<p><strong>We never access your code</strong>\n" +
            "We do not process, send, or store your proprietary code. We only provide metrics about music listening, and we make it easy to see the data we collect.</p>\n\n" +

            "<p><strong>Your data is private</strong>\n" +
            "We will never share your individually identifiable data with your boss. In the future, we will roll up data into groups and teams but we will keep your data anonymized.</p>\n\n" +

            "<p><strong>Free for you, forever</strong>\n" +
            "We provide 90 days of data history for free, forever. In the future, we will provide premium plans for advanced features and historical data access.</p>\n";

    public static void main(String[] args) {
        String parameter = args[0];

        String xmlFilePath = System.getProperty("user.dir") + "\\resources\\META-INF\\plugin.xml";

        try {

            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();

            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();

            Document document = documentBuilder.parse(xmlFilePath);
            if(parameter.equals("code-time")) {

                Node id = document.getElementsByTagName("id").item(0);
                id.setTextContent(codeTimeId);

                // Remove tool window
                Element startup = document.createElement("postStartupActivity");
                startup.setAttribute("implementation", "com.softwareco.intellij.plugin.PluginPostStartupActivity");

                Node extentions = document.getElementsByTagName("extensions").item(0);
                extentions.setTextContent("");
                extentions.appendChild(document.createTextNode("\n"));
                extentions.appendChild(startup);
                extentions.appendChild(document.createTextNode("\n"));

                //Remove old group element
                Node oldgrp = document.getElementsByTagName("group").item(0);
                Node acts = document.getElementsByTagName("actions").item(0);
                acts.removeChild(oldgrp);
                acts.setTextContent("");
                acts.appendChild(document.createTextNode("\n"));

                //Create new elements
                Element group = document.createElement("group");
                group.setAttribute("class", "com.softwareco.intellij.plugin.actions.CustomDefaultActionGroup");
                group.setAttribute("description", "Code Time menu actions");
                group.setAttribute("id", "CustomDefaultActionGroup");
                group.setAttribute("text", codeTimeName);

                Element addgroup = document.createElement("add-to-group");
                addgroup.setAttribute("anchor", "before");
                addgroup.setAttribute("group-id", "MainMenu");
                addgroup.setAttribute("relative-to-action", "HelpMenu");

                Element act = document.createElement("action");
                act.setAttribute("class", "com.softwareco.intellij.plugin.actions.CodeTimeMetricsAction");
                act.setAttribute("description", "View your latest coding metrics");
                act.setAttribute("id", "CodeTimeMetrics");
                act.setAttribute("text", "Code time dashboard");

                Element act1 = document.createElement("action");
                act1.setAttribute("class", "com.softwareco.intellij.plugin.actions.SoftwareTopFortyAction");
                act1.setAttribute("description", "Top 40 most popular songs developers around the world listen to as they code");
                act1.setAttribute("id", "SoftwareTopFortyAction");
                act1.setAttribute("text", "Software top 40");

                Element act2 = document.createElement("action");
                act2.setAttribute("class", "com.softwareco.intellij.plugin.actions.SoftwareDashboardAction");
                act2.setAttribute("description", "View your KPM metrics");
                act2.setAttribute("id", "SoftwareDashboardAction");
                act2.setAttribute("text", "Web dashboard");

                Element act3 = document.createElement("action");
                act3.setAttribute("class", "com.softwareco.intellij.plugin.actions.SoftwareLoginAction");
                act3.setAttribute("description", "To see your coding data in Code Time, please log in to your account.");
                act3.setAttribute("id", "SoftwareLoginAction");
                act3.setAttribute("text", "Log in to see your coding data");

                Element act4 = document.createElement("action");
                act4.setAttribute("class", "com.softwareco.intellij.plugin.actions.ToggleStatusBarAction");
                act4.setAttribute("description", "Toggle the Code Time status bar metrics.");
                act4.setAttribute("id", "ToggleStatusBarAction");
                act4.setAttribute("text", "Show/hide status bar metrics");

                // Append nodes here
                group.appendChild(document.createTextNode("\n"));
                group.appendChild(addgroup);
                group.appendChild(document.createTextNode("\n"));
                group.appendChild(act);
                group.appendChild(document.createTextNode("\n"));
                group.appendChild(act1);
                group.appendChild(document.createTextNode("\n"));
                group.appendChild(act2);
                group.appendChild(document.createTextNode("\n"));
                group.appendChild(act3);
                group.appendChild(document.createTextNode("\n"));
                group.appendChild(act4);
                group.appendChild(document.createTextNode("\n"));
                acts.appendChild(group);
                acts.appendChild(document.createTextNode("\n"));

                Node impl = document.getElementsByTagName("implementation-class").item(0);
                impl.setTextContent(codeTimeImpl);

                Node name = document.getElementsByTagName("name").item(0);
                name.setTextContent(codeTimeName);

                Node version = document.getElementsByTagName("version").item(0);
                version.setTextContent(codeTimeVersion);

                // Get description by tag name
                Node description = document.getElementsByTagName("description").item(0);
                description.setTextContent("");
                CDATASection cdata = document.createCDATASection(codeTimeDesc);
                description.appendChild(cdata);



                // write the DOM object to the file
                TransformerFactory transformerFactory = TransformerFactory.newInstance();

                Transformer transformer = transformerFactory.newTransformer();

                DOMSource domSource = new DOMSource(document);

                StreamResult streamResult = new StreamResult(new File(xmlFilePath));
                transformer.transform(domSource, streamResult);

                System.out.println("The XML File Updated");

                FileReader fr = null;
                FileWriter fw = null;
                try {
                    fr = new FileReader(System.getProperty("user.dir") + "\\codetime.readme.md");
                    fw = new FileWriter(System.getProperty("user.dir") + "\\README.md");
                    int c = fr.read();
                    while(c!=-1) {
                        fw.write(c);
                        c = fr.read();
                    }
                    System.out.println("The README.md File Updated");
                } catch(IOException e) {
                    e.printStackTrace();
                } finally {
                    fr.close();
                    fw.close();
                }
            } else if (parameter.equals("music-time")) {

                Node id = document.getElementsByTagName("id").item(0);
                id.setTextContent(musicTimeId);

                // Add tool window
                Element startup = document.createElement("postStartupActivity");
                startup.setAttribute("implementation", "com.softwareco.intellij.plugin.PluginPostStartupActivity");

                Element toolwindow = document.createElement("toolWindow");
                toolwindow.setAttribute("anchor", "right");
                toolwindow.setAttribute("factoryClass", "com.softwareco.intellij.plugin.actions.MusicToolWindowFactory");
                toolwindow.setAttribute("icon", "/com/softwareco/intellij/plugin/assets/playlist.png");
                toolwindow.setAttribute("id", "PlayList");

                Node extentions = document.getElementsByTagName("extensions").item(0);
                extentions.setTextContent("");
                extentions.appendChild(document.createTextNode("\n"));
                extentions.appendChild(startup);
                extentions.appendChild(document.createTextNode("\n"));
                extentions.appendChild(toolwindow);
                extentions.appendChild(document.createTextNode("\n"));

                //Remove old group element
                Node oldgrp = document.getElementsByTagName("group").item(0);
                Node acts = document.getElementsByTagName("actions").item(0);
                acts.removeChild(oldgrp);
                acts.setTextContent("");
                acts.appendChild(document.createTextNode("\n"));

                //Create new elements
                Element group = document.createElement("group");
                group.setAttribute("class", "com.softwareco.intellij.plugin.actions.CustomMusicActionGroup");
                group.setAttribute("description", "Music Time menu actions");
                group.setAttribute("id", "CustomDefaultActionGroup1");
                group.setAttribute("text", musicTimeName);

                Element addgroup = document.createElement("add-to-group");
                addgroup.setAttribute("anchor", "before");
                addgroup.setAttribute("group-id", "MainMenu");
                addgroup.setAttribute("relative-to-action", "HelpMenu");

                Element act1 = document.createElement("action");
                act1.setAttribute("class", "com.softwareco.intellij.plugin.actions.MusicDashboardAction");
                act1.setAttribute("description", "View your latest music metrics on editor");
                act1.setAttribute("id", "MusicDashboardAction");
                act1.setAttribute("text", "Music Time Dashboard");

                Element act2 = document.createElement("action");
                act2.setAttribute("class", "com.softwareco.intellij.plugin.actions.SubmitGitIssueAction");
                act2.setAttribute("description", "Encounter a bug? Submit an issue on our GitHub page");
                act2.setAttribute("id", "SubmitGitIssueAction");
                act2.setAttribute("text", "Submit an issue on GitHub");

                Element act3 = document.createElement("action");
                act3.setAttribute("class", "com.softwareco.intellij.plugin.actions.SubmitFeedbackAction");
                act3.setAttribute("description", "Send us an email at cody@software.com");
                act3.setAttribute("id", "SubmitFeedbackAction");
                act3.setAttribute("text", "Submit Feedback");

                Element act4 = document.createElement("action");
                act4.setAttribute("class", "com.softwareco.intellij.plugin.actions.ConnectSpotifyAction");
                act4.setAttribute("description", "Connect to the Spotify.");
                act4.setAttribute("id", "ConnectSpotifyAction");
                act4.setAttribute("text", "Connect Spotify");

                Element act5 = document.createElement("action");
                act5.setAttribute("class", "com.softwareco.intellij.plugin.actions.DisconnectSpotifyAction");
                act5.setAttribute("description", "Disconnect to the Spotify.");
                act5.setAttribute("id", "DisconnectSpotifyAction");
                act5.setAttribute("text", "Disconnect Spotify");

                Element act6 = document.createElement("action");
                act6.setAttribute("class", "com.softwareco.intellij.plugin.actions.ConnectSlackAction");
                act6.setAttribute("description", "To share a playlist or track on Slack, Please connect your account");
                act6.setAttribute("id", "ConnectSlackAction");
                act6.setAttribute("text", "Connect Slack");

                Element act7 = document.createElement("action");
                act7.setAttribute("class", "com.softwareco.intellij.plugin.actions.DisconnectSlackAction");
                act7.setAttribute("description", "Disconnect to the Slack");
                act7.setAttribute("id", "DisconnectSlackAction");
                act7.setAttribute("text", "Disconnect Slack");

                Element sep1 = document.createElement("separator");

                // Append nodes here
                group.appendChild(document.createTextNode("\n"));
                group.appendChild(addgroup);
                group.appendChild(document.createTextNode("\n"));
                group.appendChild(act1);
                group.appendChild(document.createTextNode("\n"));
                group.appendChild(act2);
                group.appendChild(document.createTextNode("\n"));
                group.appendChild(act3);
                group.appendChild(document.createTextNode("\n"));
                group.appendChild(sep1);
                group.appendChild(document.createTextNode("\n"));
                group.appendChild(act4);
                group.appendChild(document.createTextNode("\n"));
                group.appendChild(act5);
                group.appendChild(document.createTextNode("\n"));
                group.appendChild(act6);
                group.appendChild(document.createTextNode("\n"));
                group.appendChild(act7);
                group.appendChild(document.createTextNode("\n"));
                acts.appendChild(group);
                acts.appendChild(document.createTextNode("\n"));

                Node impl = document.getElementsByTagName("implementation-class").item(0);
                impl.setTextContent(musicTimeImpl);

                Node name = document.getElementsByTagName("name").item(0);
                name.setTextContent(musicTimeName);

                Node version = document.getElementsByTagName("version").item(0);
                version.setTextContent(musicTimeVersion);

                // Get description by tag name
                Node description = document.getElementsByTagName("description").item(0);
                description.setTextContent("");
                CDATASection cdata = document.createCDATASection(musicTimeDesc);
                description.appendChild(cdata);



                // write the DOM object to the file
                TransformerFactory transformerFactory = TransformerFactory.newInstance();

                Transformer transformer = transformerFactory.newTransformer();

                DOMSource domSource = new DOMSource(document);

                StreamResult streamResult = new StreamResult(new File(xmlFilePath));
                transformer.transform(domSource, streamResult);

                System.out.println("The XML File Updated");

                FileReader fr = null;
                FileWriter fw = null;
                try {
                    fr = new FileReader(System.getProperty("user.dir") + "\\musictime.readme.md");
                    fw = new FileWriter(System.getProperty("user.dir") + "\\README.md");
                    int c = fr.read();
                    while(c!=-1) {
                        fw.write(c);
                        c = fr.read();
                    }
                    System.out.println("The README.md File Updated");
                } catch(IOException e) {
                    e.printStackTrace();
                } finally {
                    fr.close();
                    fw.close();
                }
            } else {
                System.out.println("ERROR: Unable to update");
                System.out.println("Usage: java Deployer <code-time|music-time>");
            }

        } catch (ParserConfigurationException pce) {
            pce.printStackTrace();
        } catch (TransformerException tfe) {
            tfe.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } catch (SAXException sae) {
            sae.printStackTrace();
        }
    }
}
