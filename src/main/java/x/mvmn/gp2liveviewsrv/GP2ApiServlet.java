package x.mvmn.gp2liveviewsrv;

import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import x.mvmn.gphoto2.jna.Gphoto2Library;
import x.mvmn.jlibgphoto2.GP2AutodetectCameraHelper;
import x.mvmn.jlibgphoto2.GP2AutodetectCameraHelper.CameraListItemBean;
import x.mvmn.jlibgphoto2.GP2Camera;
import x.mvmn.jlibgphoto2.GP2Context;
import x.mvmn.jlibgphoto2.GP2PortInfoList;
import x.mvmn.jlibgphoto2.exception.GP2Exception;

public class GP2ApiServlet extends HttpServlet {
	private static final long serialVersionUID = 3186442803531247173L;

	protected final Gson GSON = new GsonBuilder().create();
	protected final AtomicBoolean liveViewEnabled = new AtomicBoolean(true);
	protected static final AtomicBoolean LIVE_VIEW_IN_PROGRESS = new AtomicBoolean(false);

	private static final byte[] PREFIX;
	private static final byte[] SEPARATOR;
	static {
		byte[] prefix = null;
		byte[] separator = null;
		try {
			prefix = ("--BoundaryString\r\n" + "Content-type: image/jpeg\r\n" + "Content-Length: ").getBytes("UTF-8");
			separator = "\r\n\r\n".getBytes("UTF-8");
		} catch (UnsupportedEncodingException notGonnaHappen) {
			// Will never happen
			throw new RuntimeException(notGonnaHappen);
		}
		PREFIX = prefix;
		SEPARATOR = separator;
	}

	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response) {
		try {
			final String requestPath = request.getServletPath() + (request.getPathInfo() != null ? request.getPathInfo() : "");
			if (requestPath.equals("/stopLiveView")) {
				stopLiveViewWaitUntilEffective();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) {
		try {
			final String requestPath = request.getServletPath() + (request.getPathInfo() != null ? request.getPathInfo() : "");
			if (requestPath.equals("/cameras")) {
				GP2Context context = new GP2Context();
				List<CameraListItemBean> detectedCameras = GP2AutodetectCameraHelper.autodetectCameras(context);
				Map<String, Object> result = new HashMap<String, Object>();
				result.put("cameras", detectedCameras);
				writeJson(result, response);
			} else if (requestPath.equals("/liveView")) {
				String cameraParam = request.getParameter("camera");
				GP2Camera camera = null;
				GP2PortInfoList portInfoList = null;
				try {
					portInfoList = new GP2PortInfoList();
					try {
						camera = new GP2Camera(portInfoList.getByPath(cameraParam));
					} catch (GP2Exception e) {
						if (e.getCode() == Gphoto2Library.GP_ERROR_CAMERA_BUSY) {
							System.err.println("Can't start liveview: camera busy");
						} else if (e.getCode() == Gphoto2Library.GP_ERROR_IO_USB_CLAIM) {
							System.err.println("Can't start liveview: USB busy");
						} else if (e.getCode() == Gphoto2Library.GP_ERROR_NOT_SUPPORTED) {
							System.err.println("Can't start liveview: not supported");
						} else {
							System.err.println("Can't start liveview: camera error " + e.getMessage());
						}
					} catch (Exception e) {
						camera = null;
						e.printStackTrace();
					}

					if (camera != null) {
						response.setContentType("multipart/x-mixed-replace; boundary=--BoundaryString");
						final OutputStream outputStream = response.getOutputStream();
						byte[] jpeg;
						stopLiveViewWaitUntilEffective();
						if (LIVE_VIEW_IN_PROGRESS.compareAndSet(false, true)) {
							liveViewEnabled.set(true);
							while (liveViewEnabled.get()) {
								try {
									jpeg = camera.capturePreview();
									outputStream.write(PREFIX);
									outputStream.write(String.valueOf(jpeg.length).getBytes("UTF-8"));
									outputStream.write(SEPARATOR);
									outputStream.write(jpeg);
									outputStream.write(SEPARATOR);
									outputStream.flush();
									System.gc();
									Thread.yield();
								} catch (final EOFException e) {
									// This just means user closed preview
									liveViewEnabled.set(false);
									break;
								} catch (final Exception e) {
									e.printStackTrace();
									System.err.println("Live view stopped: " + e.getClass().getName() + " " + e.getMessage());
									break;
								}
							}
							LIVE_VIEW_IN_PROGRESS.set(false);
						} else {
							response.setStatus(HttpServletResponse.SC_CONFLICT);
						}
					} else {
						response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
					}
				} finally {
					if (portInfoList != null) {
						portInfoList.close();
					}
					if (camera != null) {
						camera.close();
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	protected void stopLiveViewWaitUntilEffective() {
		liveViewEnabled.set(false);
		while (LIVE_VIEW_IN_PROGRESS.get()) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
		}
	}

	protected void writeJson(Object object, HttpServletResponse response) throws IOException {
		response.setContentType("application/json");
		GSON.toJson(object, response.getWriter());
	}
}
