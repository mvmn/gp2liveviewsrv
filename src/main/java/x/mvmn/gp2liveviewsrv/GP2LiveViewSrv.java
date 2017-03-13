package x.mvmn.gp2liveviewsrv;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

public class GP2LiveViewSrv {

	public static void main(String args[]) throws Exception {
		Integer port = null;
		final Options cliOptions = new Options();

		cliOptions.addOption("imagesFolder", true, "Images folder (defaults to ~/gp2liveview/images).");
		cliOptions.addOption("port", true, "HTTP port.");
		cliOptions.addOption("authToken", true, "Optional authToken - to be sent with .");

		final CommandLine commandLine = new PosixParser().parse(cliOptions, args);
		if (commandLine.hasOption("port")) {
			String portOptionVal = commandLine.getOptionValue("port");
			try {
				int parsedPort = Integer.parseInt(portOptionVal.trim());
				if (parsedPort < 1 || parsedPort > 65535) {
					throw new RuntimeException("Bad port value: " + parsedPort);
				} else {
					port = parsedPort;
				}
			} catch (NumberFormatException e) {
				throw new RuntimeException("Unable to parse port parameter as integer: '" + portOptionVal + "'.");
			}
		}

		File userHome = new File(System.getProperty("user.home"));

		File imagesFolder;
		if (commandLine.hasOption("imagesFolder")) {
			imagesFolder = new File(commandLine.getOptionValue("imagesFolder"));
		} else {
			imagesFolder = new File(new File(userHome, ".gp2liveview"), "images");
		}
		imagesFolder.mkdirs();

		int portVal = port != null ? port.intValue() : 8080;
		String authToken = commandLine.hasOption("authToken") ? commandLine.getOptionValue("authToken") : null;

		new GP2LiveViewSrv(portVal, authToken, imagesFolder).start();
	}

	protected final Server server;

	public GP2LiveViewSrv(int port, final String authToken, final File imagesFolder) {
		this.server = new Server(port);
		this.server.setStopAtShutdown(true);

		ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
		server.setHandler(context);
		context.addServlet(new ServletHolder(new ImagesServlet(imagesFolder)), "/images/*");
		context.addServlet(new ServletHolder(new GP2ApiServlet(imagesFolder)), "/");
		if (authToken != null) {
			Filter tokenAuthFilter = new Filter() {

				public void init(FilterConfig filterConfig) throws ServletException {
				}

				public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
					boolean authorized = false;
					if (request instanceof HttpServletRequest) {
						HttpServletRequest httpRequest = (HttpServletRequest) request;
						if (authToken.equals(httpRequest.getHeader("X-AuthToken"))) {
							authorized = true;
							chain.doFilter(httpRequest, response);
						}
					}
					if (!authorized) {
						if (response instanceof HttpServletResponse) {
							((HttpServletResponse) response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
						}
					}
				}

				public void destroy() {
				}
			};

			// CORS
			context.addFilter(new FilterHolder(new Filter() {
				public void init(FilterConfig filterConfig) throws ServletException {
				}

				public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
					if (response instanceof HttpServletResponse) {
						((HttpServletResponse) response).setHeader("Access-Control-Allow-Origin", "*");
					}
					chain.doFilter(request, response);
				}

				public void destroy() {
				}
			}), "/*", EnumSet.of(DispatcherType.REQUEST));
			context.addFilter(new FilterHolder(tokenAuthFilter), "/*", EnumSet.of(DispatcherType.REQUEST));
		}
	}

	public void start() {
		try {
			this.server.start();
		} catch (Exception e) {
			throw new RuntimeException("Failed to start Jetty", e);
		}
	}
}
