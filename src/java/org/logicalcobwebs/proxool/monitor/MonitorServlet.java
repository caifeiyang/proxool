/*
 * This software is released under the Apache Software Licence. See
 * package.html for details. The latest version is available at
 * http://proxool.sourceforge.net
 */
package org.logicalcobwebs.proxool.monitor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.logicalcobwebs.proxool.ConnectionPoolDefinitionIF;
import org.logicalcobwebs.proxool.ProxoolException;
import org.logicalcobwebs.proxool.ProxoolFacade;
import org.logicalcobwebs.proxool.stats.SnapshotIF;
import org.logicalcobwebs.proxool.stats.StatisticsIF;

import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;

/**
 * Use this to monitor each pool within Proxool. It acts like a normal
 * servlet., so just configure it within your web app as you see fit.
 * For example, within web.xml:
 *
 * <pre>
 *   &lt;servlet&gt;
 *       &lt;servlet-name&gt;Monitor&lt;/servlet-name&gt;
 *       &lt;servlet-class&gt;org.logicalcobwebs.proxool.monitor.MonitorServlet&lt;/servlet-class&gt;
 *   &lt;/servlet&gt;
 *
 *   &lt;servlet-mapping&gt;
 *       &lt;servlet-name&gt;Monitor&lt;/servlet-name&gt;
 *       &lt;url-pattern&gt;/monitor&lt;/url-pattern&gt;
 *   &lt;/servlet-mapping&gt;
 * </pre>
 *
 * @version $Revision: 1.1 $, $Date: 2003/01/31 00:38:22 $
 * @author bill
 * @author $Author: billhorsman $ (current maintainer)
 * @since Proxool 0.7
 */
public class MonitorServlet extends HttpServlet {
    private static final Log LOG = LogFactory.getLog(MonitorServlet.class);

    private int width = 300;

    private int height = 5;

    protected static final String ACTION_LIST = "list";
    private static final String ACTION_STATS = "stats";
    protected static final String ACTION_CHART = "chart";
    protected static final String TYPE = "type";
    protected static final String TYPE_CONNECTIONS = "1";
    protected static final String TYPE_ACTIVITY_LEVEL = "2";
    private static final String STYLE_CAPTION = "text-align: right; color: #333333;";
    private static final String STYLE_DATA = "background: white;";
    private static final String STYLE_NO_DATA = "color: #666666;";
    private static final int DATE_OFFSET = 3600000;
    private static final DateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
    private static final DateFormat dateFormat = new SimpleDateFormat("DD-MMM-yyyy HH:mm:ss");
    private static final DecimalFormat decimalFormat = new DecimalFormat("0.00");
    private static final Color COLOR_ACTIVE = Color.red;
    private static final Color COLOR_AVAILABLE = Color.green;
    private static final Color COLOR_SPARE = Color.decode("#eeeeee");

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doGet(request, response);
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        response.setHeader("Pragma", "no-cache");
        String link = request.getRequestURI();

        // Check the action, and default to stats
        String action = request.getParameter("action");
        if (action == null) {
            action = ACTION_STATS;
        }

        if (action.equals(ACTION_CHART)) {
            response.setContentType("image/png");
            String[] c = request.getParameterValues("c");
            String[] l = request.getParameterValues("l");
            String d = request.getParameter("d");
            drawBarChart(response.getOutputStream(), c, l, d);
        } else {

            // Check the alias and if not defined and there is only one
            // then use that. Otherwise show the list.
            String alias = request.getParameter("alias");
            String[] aliases = ProxoolFacade.getAliases();
            if (alias == null) {
                if (aliases.length == 1) {
                    alias = aliases[0];
                } else {
                    action = ACTION_LIST;
                }
            }

            // Check we can find the pool. If not, show the list
            if (alias != null) {
                try {
                    ProxoolFacade.getConnectionPoolDefinition(alias);
                } catch (ProxoolException e) {
                    action = ACTION_LIST;
                }
            }


            try {
                if (action.equals(ACTION_LIST)) {
                    response.setContentType("text/html");
                    doList(response.getOutputStream(), alias, link);
                } else if (action.equals(ACTION_STATS)) {
                    response.setContentType("text/html");
                    doStats(response.getOutputStream(), alias, link);
                } else {
                    LOG.error("Unrecognised action '" + action + "'");
                }
            } catch (ProxoolException e) {
                LOG.error("Problem", e);
            }

        }
    }

    private void drawBarChart(ServletOutputStream out, String[] colors, String[] lengths, String divisions) throws IOException {
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        Graphics2D g2d = bufferedImage.createGraphics();
        g2d.setColor(Color.decode("#" + colors[0]));
        g2d.fillRect(0, 0, width, height);

        int fullLength = Integer.parseInt(lengths[0]);
        int left = 0;
        int pixels = 0;
        for (int i = 1; i < colors.length; i++) {
            int length = Integer.parseInt(lengths[i]);
            pixels = width * length / fullLength;
            g2d.setColor(Color.decode("#" + colors[i]));
            g2d.fillRect(left, 0, pixels, height - 1);
            left += pixels;
        }

        g2d.setColor(Color.decode("#666666"));
        int d = Integer.parseInt(divisions);
        for (int i = 0; i < d; i++) {
            int x = i * width / d;
            g2d.drawLine(x, 0, x, height - 1);
        }

        g2d.dispose();
        ImageIO.write(bufferedImage, "png", out);
    }

    private void doStats(ServletOutputStream out, String alias, String link) throws ProxoolException, IOException {
        openHtml(out);
        doDefinition(out, alias, link);
        doSnapshot(out, alias, link);
        doStatistics(out, alias, link);
        closeHtml(out);
    }

    private void doStatistics(ServletOutputStream out, String alias, String link) throws ProxoolException, IOException {
        StatisticsIF[] statisticsArray = ProxoolFacade.getStatistics(alias);
        SnapshotIF snapshot = ProxoolFacade.getSnapshot(alias);
        ConnectionPoolDefinitionIF cpd = ProxoolFacade.getConnectionPoolDefinition(alias);

        for (int i = 0; i < statisticsArray.length; i++) {
            StatisticsIF statistics = statisticsArray[i];
            out.print("<b>Statistics</b> from ");
            out.print(timeFormat.format(statistics.getStartDate()));
            out.print(" to ");
            out.print(timeFormat.format(statistics.getStopDate()));

            openTable(out);

            // Served
            printDefintionEntry(out, "Served", statistics.getServedCount() + " (" + decimalFormat.format(statistics.getServedPerSecond()) + "/s)");

            // Refused
            printDefintionEntry(out, "Refused", statistics.getRefusedCount() + " (" + decimalFormat.format(statistics.getRefusedPerSecond()) + "/s)");

            // averageActiveTime
            printDefintionEntry(out, "Average active time", decimalFormat.format(statistics.getAverageActiveTime()) + "s");

            // activityLevel
            StringBuffer activityLevelBuffer = new StringBuffer();
            activityLevelBuffer.append((int) (100 * statistics.getAverageActiveCount() / snapshot.getMaximumConnectionCount()));
            activityLevelBuffer.append("%<br/>");
            activityLevelBuffer.append("<img style=\"margin: 4px;\" src=\"");
            activityLevelBuffer.append(link);
            activityLevelBuffer.append("?action=chart&c=eeeeee&c=0000ff&l=100&l=");
            activityLevelBuffer.append((int) (100 * statistics.getAverageActiveCount() / cpd.getMaximumConnectionCount()));
            activityLevelBuffer.append("&d=10\" width=\"300\" height=\"5\" alt=\"...\">");
            printDefintionEntry(out, "Activity level", activityLevelBuffer.toString());

            closeTable(out);
        }
    }

    private void doDefinition(ServletOutputStream out, String alias, String link) throws ProxoolException, IOException {
        ConnectionPoolDefinitionIF cpd = ProxoolFacade.getConnectionPoolDefinition(alias);

        out.print("<b>Defintition</b> for ");
        out.println(alias);
        openTable(out);

        // url
        printDefintionEntry(out, "URL", cpd.getUrl());

        // driver
        printDefintionEntry(out, "Driver", cpd.getDriver());

        // minimumConnectionCount and maximumConnectionCount
        printDefintionEntry(out, "Connections", cpd.getMinimumConnectionCount() + " (min), " + cpd.getMaximumConnectionCount() + " (max)");

        // prototypeCount
        printDefintionEntry(out, "Prototyping", cpd.getPrototypeCount() > 0 ? String.valueOf(cpd.getPrototypeCount()) : null);

        // maximumConnectionLifetime
        printDefintionEntry(out, "Connection Lifetime", timeFormat.format(new Date(cpd.getMaximumConnectionLifetime() - DATE_OFFSET)));

        // maximumActiveTime
        printDefintionEntry(out, "Maximum active timeFormat", timeFormat.format(new Date(cpd.getMaximumActiveTime() - DATE_OFFSET)));
        printDefintionEntry(out, "House keeping sleep timeFormat", (cpd.getHouseKeepingSleepTime() / 1000) + "s");

        // houseKeepingTestSql
        printDefintionEntry(out, "House keeping test SQL", cpd.getHouseKeepingTestSql());

        // fatalSqlExceptions
        String fatalSqlExceptions = null;
        if (cpd.getFatalSqlExceptions() != null && cpd.getFatalSqlExceptions().size() > 0) {
            StringBuffer fatalSqlExceptionsBuffer = new StringBuffer();
            Iterator i = cpd.getFatalSqlExceptions().iterator();
            while (i.hasNext()) {
                String s = (String) i.next();
                fatalSqlExceptionsBuffer.append(s);
                fatalSqlExceptionsBuffer.append(i.hasNext() ? ", " : "");
            }
            fatalSqlExceptions = fatalSqlExceptionsBuffer.toString();
        }
        printDefintionEntry(out, "Fatal SQL exceptions", fatalSqlExceptions);

        // statistics
        printDefintionEntry(out, "Statistics", cpd.getStatistics());

        closeTable(out);

    }

    private void doSnapshot(ServletOutputStream out, String alias, String link) throws IOException, ProxoolException {
        SnapshotIF snapshot = ProxoolFacade.getSnapshot(alias);
        ConnectionPoolDefinitionIF cpd = ProxoolFacade.getConnectionPoolDefinition(alias);

        out.print("<b>Snapshot</b> at ");
        out.println(timeFormat.format(snapshot.getSnapshotDate()));
        openTable(out);

        // dateStarted
        printDefintionEntry(out, "Start date", dateFormat.format(snapshot.getDateStarted()));

        // connections
        StringBuffer connectionsBuffer = new StringBuffer();
        connectionsBuffer.append(snapshot.getActiveConnectionCount());
        connectionsBuffer.append(" (<font color=\"");
        connectionsBuffer.append(COLOR_ACTIVE);
        connectionsBuffer.append("\">active</font>), ");
        connectionsBuffer.append(snapshot.getAvailableConnectionCount());
        connectionsBuffer.append(" (<font color=\"");
        connectionsBuffer.append(COLOR_AVAILABLE);
        connectionsBuffer.append("\">available</font>), ");
        if (snapshot.getOfflineConnectionCount() > 0) {
            connectionsBuffer.append(snapshot.getOfflineConnectionCount());
            connectionsBuffer.append(" (offline), ");
        }
        connectionsBuffer.append(snapshot.getMaximumConnectionCount());
        connectionsBuffer.append(" (<font color=\"");
        connectionsBuffer.append(COLOR_SPARE);
        connectionsBuffer.append("\">max</font>)<br/>");
        connectionsBuffer.append("<img style=\"margin: 4px;\" src=\"");
        connectionsBuffer.append(link);
        connectionsBuffer.append("?action=chart&c=eeeeee&c=ff0000&c=00ff00&l=");
        connectionsBuffer.append(cpd.getMaximumConnectionCount());
        connectionsBuffer.append("&l=");
        connectionsBuffer.append(snapshot.getActiveConnectionCount());
        connectionsBuffer.append("&l=");
        connectionsBuffer.append(snapshot.getAvailableConnectionCount());
        connectionsBuffer.append("&d=");
        connectionsBuffer.append(cpd.getMaximumConnectionCount());
        connectionsBuffer.append("\" width=\"300\" height=\"5\" alt=\"...\">");
        printDefintionEntry(out, "Connections", connectionsBuffer.toString());

        // servedCount
        printDefintionEntry(out, "Served", String.valueOf(snapshot.getServedCount()));

        // refusedCount
        printDefintionEntry(out, "Refused", String.valueOf(snapshot.getRefusedCount()));

        closeTable(out);
    }

    private void openHtml(ServletOutputStream out) throws IOException {
        out.println("<html><header><title>Proxool Monitor</title></header><body BGCOLOR=\"#eeeeee\">");
    }

    private void closeHtml(ServletOutputStream out) throws IOException {
        out.println("</body></html>");
    }

    private void openTable(ServletOutputStream out) throws IOException {
        out.println("<table width=\"550\" cellpadding=\"2\" cellspacing=\"2\" border=\"0\" bgcolor=\"#EEEEEE\" style=\"border: 1px solid black\">");
        out.println("  <tbody>");
    }

    private void closeTable(ServletOutputStream out) throws IOException {
        out.println("  </tbody>");
        out.println("</table>");
        out.println("<br/>");
    }


    private void printDefintionEntry(ServletOutputStream out, String name, String value) throws IOException {
        out.println("    <tr>");
        out.print("      <td width=\"200\" valign=\"top\" style=\"" + STYLE_CAPTION + "\">");
        out.print(name);
        out.println("</td>");
        if (value != null) {
            out.print("      <td style=\"" + STYLE_DATA + "\">");
            out.print(value);
        } else {
            out.print("      <td style=\"" + STYLE_NO_DATA + "\">off");
        }
        out.print("</td>");
        out.println("    </tr>");
    }

    private void doList(ServletOutputStream out, String alias, String link) throws IOException, ProxoolException {
        openHtml(out);

        out.print("<b>Choose</b> a pool");
        out.println("<table cellpadding=\"2\" cellspacing=\"2\" border=\"0\" bgcolor=\"#EEEEEE\" style=\"border: 1px solid black\">");
        out.println("  <tbody>");

        String[] aliases = ProxoolFacade.getAliases();
        for (int i = 0; i < aliases.length; i++) {
            String a = aliases[i];
            String style = "";
            if (a.equals(alias)) {
                style = "background: white;";
            }
            ConnectionPoolDefinitionIF cpd = ProxoolFacade.getConnectionPoolDefinition(a);
            out.println("    <tr style=\"" + style + "\">");
            out.print("      <td><a href=\"" + link + "?alias=" + a + "\">");
            out.print(a);
            out.println("</a></td>");
            out.print("      <td>");
            out.print(cpd.getUrl());
            out.println("</td>");
            out.println("    </tr>");
        }

        closeTable(out);
        closeHtml(out);
    }
}


/*
 Revision history:
 $Log: MonitorServlet.java,v $
 Revision 1.1  2003/01/31 00:38:22  billhorsman
 *** empty log message ***

 */