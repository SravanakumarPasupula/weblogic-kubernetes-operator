// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.applications.clusterview;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Hashtable;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import weblogic.health.HealthState;

import weblogic.management.jmx.MBeanServerInvocationHandler;
import weblogic.management.mbeanservers.domainruntime.DomainRuntimeServiceMBean;
import weblogic.management.mbeanservers.runtime.RuntimeServiceMBean;
import weblogic.management.runtime.ClusterRuntimeMBean;
import weblogic.management.runtime.ServerRuntimeMBean;

/**
 * Servlet to print all MBeans names and attributes in the server runtime.
 */
public class ClusterViewServlet extends HttpServlet {

  Context ctx = null;
  MBeanServer localMBeanServer;
  ServerRuntimeMBean serverRuntime;
  RuntimeServiceMBean runtimeService;
  MBeanServer domainMBeanServer;
  DomainRuntimeServiceMBean domainRuntimeServiceMbean;

  @Override
  public void init(ServletConfig config) throws ServletException {
    try {
      ctx = new InitialContext();
      System.out.println("ITTESTS:>>>>Looking up server runtime mbean server");
      localMBeanServer = (MBeanServer) ctx.lookup("java:comp/env/jmx/runtime");
      // get ServerRuntimeMBean
      ObjectName runtimeserviceObjectName = new ObjectName(RuntimeServiceMBean.OBJECT_NAME);
      runtimeService = (RuntimeServiceMBean) MBeanServerInvocationHandler
          .newProxyInstance(localMBeanServer, runtimeserviceObjectName);
      serverRuntime = runtimeService.getServerRuntime();
      System.out.println("ITTESTS:>>>>Found server runtime mbean server for server: " + serverRuntime.getName());

      if (serverRuntime.isAdminServer()) {
        try {
          System.out.println("ITTESTS:>>>>Looking up domain runtime mbean in server : " + serverRuntime.getName());
          domainMBeanServer = (MBeanServer) ctx.lookup("java:comp/env/jmx/domainRuntime");
          ObjectName domainServiceObjectName = new ObjectName(DomainRuntimeServiceMBean.OBJECT_NAME);
          domainRuntimeServiceMbean = (DomainRuntimeServiceMBean) MBeanServerInvocationHandler
              .newProxyInstance(domainMBeanServer, domainServiceObjectName);
          System.out.println("ITTESTS:>>>>Found domain runtime mbean in server : " + serverRuntime.getName());
        } catch (MalformedObjectNameException | NamingException ex) {
          System.out.println("ITTESTS:>>>>Looking up domain runtime mbean in server : " + serverRuntime.getName() + " threw exception");
          System.out.println("ITTESTS:>>>>" + ex.getMessage());
        }
      }

      try {
        System.out.println("ITTESTS:>>>>Looking up server : " + serverRuntime.getName() + " in JNDI tree");
        ctx.lookup(serverRuntime.getName());
      } catch (NameNotFoundException nnfe) {
        System.out.println("ITESTS:>>>>>>Server not found in JNDI tree, Binding " + serverRuntime.getName() + " in JNDI tree");
        ctx.bind("Bound" + serverRuntime.getName(), serverRuntime.getName());
        System.out.println("ITESTS:>>>>>>Bound " + serverRuntime.getName() + " in JNDI tree");
      }
    } catch (MalformedObjectNameException | NamingException ex) {
      System.out.println("ITTESTS:>>>>ClusterViewServlet.init() threw exception");
      System.out.println("ITTESTS:>>>>" + ex.getMessage());
    }
  }

  @Override
  public void destroy() {
    try {
      System.out.println("ITTESTS:>>>>Unbinding server : " + serverRuntime.getName());
      ctx.unbind(serverRuntime.getName());
      System.out.println("ITTESTS:>>>>Closing context in server : " + serverRuntime.getName());
      ctx.close();
    } catch (NamingException ex) {
      System.out.println("ITTESTS:>>>>ClusterViewServlet.destroy() threw exception");
      System.out.println("ITTESTS:>>>>" + ex.getMessage());
    }
  }

  /**
   * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
   *
   * @param request servlet request
   * @param response servlet response
   * @throws ServletException if a servlet-specific error occurs
   * @throws IOException if an I/O error occurs
   */
  protected void processRequest(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    response.setContentType("text/html;charset=UTF-8");
    try (PrintWriter out = response.getWriter()) {
      out.println("<!DOCTYPE html>");
      out.println("<html>");
      out.println("<head>");
      out.println("<title>ClusterViewServlet</title>");
      out.println("</head>");
      out.println("<body>");
      out.println("<pre>");

      String queryServers = request.getParameter("queryServers");
      if (queryServers != null) {
        // print all mbeans and its attributes in the server runtime
        out.println("Querying server: " + localMBeanServer.toString());
        Set<ObjectInstance> mbeans = localMBeanServer.queryMBeans(null, null);
        for (ObjectInstance mbeanInstance : mbeans) {
          out.println("<br>ObjectName: " + mbeanInstance.getObjectName() + "<br>");
          MBeanInfo mBeanInfo = localMBeanServer.getMBeanInfo(mbeanInstance.getObjectName());
          MBeanAttributeInfo[] attributes = mBeanInfo.getAttributes();
          for (MBeanAttributeInfo attribute : attributes) {
            out.println("<br>Type: " + attribute.getType() + "<br>");
            out.println("<br>Name: " + attribute.getName() + "<br>");
          }
        }
      }

      ClusterRuntimeMBean clusterRuntime = serverRuntime.getClusterRuntime();
      //if the server is part of a cluster get its cluster details
      if (clusterRuntime != null) {
        String[] serverNames = clusterRuntime.getServerNames();
        out.println("Alive:" + clusterRuntime.getAliveServerCount());
        out.println("Health:" + clusterRuntime.getHealthState().getState());
        out.println("Members:" + String.join(",", serverNames));
        out.println("ServerName:" + serverRuntime.getName());

        try {
          int random = (int) (Math.random() * 1000000);
          String randomString = "ms" + random;
          ctx.bind(randomString, randomString);
        } catch (NameNotFoundException nnfex) {
          out.println(nnfex.getMessage());
        }

        // lookup JNDI for other clustered servers bound in tree
        for (String serverName : serverNames) {
          try {
            if (ctx.lookup("Bound" + serverName) != null) {
              out.println("Bound:" + serverName);
            }
          } catch (NameNotFoundException nnfex) {
            out.println(nnfex.getMessage());
          }
        }
        if (request.getParameter("bindDomain") != null) {
          String domainName = request.getParameter("bindDomain");
          try {
            if (ctx.lookup(domainName) != null) {
              out.println("Bound:" + domainName);
            }
          } catch (NameNotFoundException nnfex) {
            ctx.bind(domainName, domainName);
            System.out.println("ITESTS:>>>>>>Bound " + domainName + " in JNDI tree");
          }
        }
        if (request.getParameter("domainTest") != null) {
          String domainName = request.getParameter("domainTest");
          try {
            if (ctx.lookup(domainName) != null) {
              out.println("Bound:" + domainName);
            }
          } catch (NameNotFoundException nnfex) {
            System.out.println("ITESTS:>>>>>>Not Bound " + domainName + " in JNDI tree");
          }
        }
        testConnection(request, out);

      } else {
        out.println(serverRuntime.getName() + ":Cluster runtime NULL <BR>");
        System.out.println("ITESTS:>>>>>>Cluster runtime is null in server:" + serverRuntime.getName());
      }

      String listServers = request.getParameter("listServers");
      if (listServers != null) {
        ServerRuntimeMBean[] serverRuntimes = domainRuntimeServiceMbean.getServerRuntimes();
        for (ServerRuntimeMBean serverRuntime : serverRuntimes) {
          out.println(serverRuntime.getName() + ":STATUS<BR>");
          int state = serverRuntime.getHealthState().getState();
          switch (state) {
            case HealthState.HEALTH_OK:
              out.print(serverRuntime.getName() + ":HEALTH_OK");
              break;
            case HealthState.HEALTH_CRITICAL:
              out.print(serverRuntime.getName() + ":HEALTH_CRITICAL");
              break;
            case HealthState.HEALTH_FAILED:
              out.print(serverRuntime.getName() + ":HEALTH_FAILED");
              break;
            case HealthState.HEALTH_OVERLOADED:
              out.print(serverRuntime.getName() + ":HEALTH_OVERLOADED");
              break;
            case HealthState.HEALTH_WARN:
              out.print(serverRuntime.getName() + ":HEALTH_WARN");
              break;
            default:
              out.print(serverRuntime.getName() + ":HEALTH_WARN");
          }
          out.println("<BR>");
        }
      }
      out.println("</pre>");
      out.println("</body>");
      out.println("</html>");
    } catch (NamingException | InstanceNotFoundException
        | IntrospectionException | ReflectionException ex) {
      Logger.getLogger(ClusterViewServlet.class.getName()).log(Level.SEVERE, null, ex);
    }
  }

  private void testConnection(HttpServletRequest request, PrintWriter out) {

    String domain = request.getParameter("domain");
    System.out.println("domain:" + domain);
    String servers = request.getParameter("servers");
    System.out.println("servers:" + servers);
    String portString = request.getParameter("port");
    System.out.println("portString:" + portString);
    String user = request.getParameter("user");
    System.out.println("user:" + user);
    String password = request.getParameter("password");
    System.out.println("password:" + password);


    out.println("Server:" + runtimeService.getServerRuntime().getName());
    JMXConnector jmxConnector = null;

    String[] managedServers = servers.split(":");
    for (String managedServer : managedServers) {
      try {
        String host = domain + "-" + managedServer;
        System.out.println("Host: " + host + " Port: " + portString + " username: " + user + " password :" + password);
        String protocol = "t3";
        Integer portInteger = Integer.valueOf(portString);
        int port = portInteger;
        Hashtable h = new Hashtable();
        h.put(Context.SECURITY_PRINCIPAL, user);
        h.put(Context.SECURITY_CREDENTIALS, password);
        h.put(JMXConnectorFactory.PROTOCOL_PROVIDER_PACKAGES, "weblogic.management.remote");
        h.put("jmx.remote.x.request.waiting.timeout", Long.valueOf(10000));
        JMXServiceURL serviceURL = new JMXServiceURL(protocol, host, port, "/jndi/" + RuntimeServiceMBean.MBEANSERVER_JNDI_NAME);
        System.out.println("Making mbean server connection with url" + serviceURL.toString());
        jmxConnector = JMXConnectorFactory.connect(serviceURL, h);
        MBeanServerConnection mbeanServer = jmxConnector.getMBeanServerConnection();
        ObjectName runtimeserviceObjectName = new ObjectName(RuntimeServiceMBean.OBJECT_NAME);
        RuntimeServiceMBean runtimeService = (RuntimeServiceMBean) MBeanServerInvocationHandler.newProxyInstance(mbeanServer, runtimeserviceObjectName);
        ServerRuntimeMBean serverRuntime = runtimeService.getServerRuntime();
        out.println("Success:" + serverRuntime.getName());
      } catch (Exception ex) {
        out.println(ex.getMessage());
      } finally {
        try {
          if (jmxConnector != null) {
            System.out.println("Closing mbean server connection");
            jmxConnector.close();
          }
        } catch (IOException ex) {
          out.println(ex.getMessage());
        }
      }
    }
  }

  protected MBeanServerConnection lookupMBeanServerConnection(String host, String portString, String user, String password, String jndiName) {
    JMXServiceURL serviceURL = null;
    MBeanServerConnection mBeanServerConnection = null;
    try {
      System.out.println("Host: " + host + " Port: " + portString + " username: " + user + " password :" + password);
      String protocol = "t3";
      Integer portInteger = Integer.valueOf(portString);
      int port = portInteger;
      Hashtable h = new Hashtable();
      h.put(Context.SECURITY_PRINCIPAL, user);
      h.put(Context.SECURITY_CREDENTIALS, password);
      h.put(JMXConnectorFactory.PROTOCOL_PROVIDER_PACKAGES, "weblogic.management.remote");
      h.put("jmx.remote.x.request.waiting.timeout", Long.valueOf(10000));
      serviceURL = new JMXServiceURL(protocol, host, port, "/jndi/" + jndiName);
      System.out.println("Making mbean server connection with url" + serviceURL.toString());
      JMXConnector jmxConnector = JMXConnectorFactory.connect(serviceURL, h);
      mBeanServerConnection = jmxConnector.getMBeanServerConnection();
    } catch (NumberFormatException | IOException e) {
      System.out.println(e.getLocalizedMessage());
    }
    return mBeanServerConnection;
  }

  /**
   * Handles the HTTP <code>GET</code> method.
   *
   * @param request servlet request
   * @param response servlet response
   * @throws ServletException if a servlet-specific error occurs
   * @throws IOException if an I/O error occurs
   */
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    processRequest(request, response);
  }

  /**
   * Handles the HTTP <code>POST</code> method.
   *
   * @param request servlet request
   * @param response servlet response
   * @throws ServletException if a servlet-specific error occurs
   * @throws IOException if an I/O error occurs
   */
  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    processRequest(request, response);
  }

  /**
   * Returns a short description of the servlet.
   *
   * @return a String containing servlet description
   */
  @Override
  public String getServletInfo() {
    return "Cluster View Servlet";
  }

}
