package x.mvmn.gp2liveviewsrv;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class ImagesServlet extends HttpServlet {

	private static final long serialVersionUID = -7220871843069688999L;
	private final File imagesFolder;
	protected final Gson GSON = new GsonBuilder().create();

	public ImagesServlet(final File imagesFolder) {
		if (!imagesFolder.exists()) {
			throw new RuntimeException("Images folder does not exist");
		}
		if (!imagesFolder.isDirectory()) {
			throw new RuntimeException("Images folder path doesn't point to a folder (must be a file)");
		}

		this.imagesFolder = imagesFolder;
	}

	protected File processRequest(HttpServletRequest request, HttpServletResponse response) {
		File result;
		try {
			String path = request.getPathInfo();
			File targetFile = new File(imagesFolder, path);
			String targetFileCanonicalPath = targetFile.getCanonicalPath();
			if (!targetFileCanonicalPath.startsWith(imagesFolder.getCanonicalPath())) {
				result = null;
				returnForbidden(request, response);
			} else if (!targetFile.exists()) {
				result = null;
				returnNotFound(request, response);
			} else {
				result = targetFile;
				response.setStatus(HttpServletResponse.SC_OK);
				MimeTypesHelper.setContentType(response, path);
			}
		} catch (Exception e) {
			result = null;
			e.printStackTrace();
			returnInternalError(request, response);
		}
		return result;
	}

	@Override
	public void doHead(final HttpServletRequest request, final HttpServletResponse response) {
		File file = processRequest(request, response);
		if (file != null && file.length() < Integer.MAX_VALUE) {
			response.setContentLength((int) file.length());
		}
	}

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) {
		String path = request.getPathInfo();
		if (path == null || path.equals("/")) {
			Map<String, List<String>> result = new HashMap<String, List<String>>();
			List<String> files = new ArrayList<String>();
			for (File file : imagesFolder.listFiles()) {
				files.add(file.getName());
			}
			result.put("files", files);
			try {
				GP2ApiServlet.writeJson(GSON, result, response);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		} else {
			File result = processRequest(request, response);
			if (result != null) {
				try {
					serveFile(result, request, response);
				} catch (Exception e) {
					e.printStackTrace();
					returnInternalError(request, response);
				}
			}
		}
	}

	private void returnNotFound(HttpServletRequest request, HttpServletResponse response) {
		response.setStatus(HttpServletResponse.SC_NOT_FOUND);
	}

	private void returnForbidden(HttpServletRequest request, HttpServletResponse response) {
		response.setStatus(HttpServletResponse.SC_FORBIDDEN);
	}

	private void returnInternalError(HttpServletRequest request, HttpServletResponse response) {
		response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
	}

	private void serveFile(File result, HttpServletRequest request, HttpServletResponse response) throws Exception {
		IOUtils.copy(new FileInputStream(result), response.getOutputStream());
	}
}
