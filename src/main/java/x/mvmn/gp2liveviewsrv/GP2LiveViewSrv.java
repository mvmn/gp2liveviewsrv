package x.mvmn.gp2liveviewsrv;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

public class GP2LiveViewSrv {

	public static void main(String args[]) throws Exception {
		Integer port = null;
		final Options cliOptions = new Options();

		cliOptions.addOption("port", true, "HTTP port.");

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

		new GP2LiveViewSrv(port != null ? port.intValue() : 8080).start();
	}

	protected final Server server;

	public GP2LiveViewSrv(int port) {
		this.server = new Server(port);
		this.server.setStopAtShutdown(true);

		ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
		server.setHandler(context);
		context.addServlet(new ServletHolder(new GP2ApiServlet()), "/");
	}

	public void start() {
		try {
			this.server.start();
		} catch (Exception e) {
			throw new RuntimeException("Failed to start Jetty", e);
		}
	}
}
