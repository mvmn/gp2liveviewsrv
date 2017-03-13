package x.mvmn.gp2liveviewsrv;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import x.mvmn.gphoto2.jna.Gphoto2Library;
import x.mvmn.jlibgphoto2.CameraConfigEntryBean;
import x.mvmn.jlibgphoto2.CameraConfigEntryBean.CameraConfigEntryType;
import x.mvmn.jlibgphoto2.CameraFileSystemEntryBean;
import x.mvmn.jlibgphoto2.GP2AutodetectCameraHelper;
import x.mvmn.jlibgphoto2.GP2AutodetectCameraHelper.CameraListItemBean;
import x.mvmn.jlibgphoto2.GP2Camera;
import x.mvmn.jlibgphoto2.GP2CameraFilesHelper;
import x.mvmn.jlibgphoto2.GP2ConfigHelper;
import x.mvmn.jlibgphoto2.GP2Context;
import x.mvmn.jlibgphoto2.GP2PortInfoList;
import x.mvmn.jlibgphoto2.exception.GP2Exception;

public class GP2ApiServlet extends HttpServlet {
	private static final long serialVersionUID = 3186442803531247173L;

	protected final Gson GSON = new GsonBuilder().create();
	protected final AtomicBoolean liveViewEnabled = new AtomicBoolean(true);

	protected final File imagesDownloadFolder;
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

	public GP2ApiServlet(final File imagesDownloadFolder) {
		this.imagesDownloadFolder = imagesDownloadFolder;
	}

	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response) {
		try {
			final String requestPath = request.getServletPath() + (request.getPathInfo() != null ? request.getPathInfo() : "");
			if (requestPath.equals("/stopLiveView")) {
				stopLiveViewWaitUntilEffective();
			} else if (requestPath.equals("/capture")) {
				stopLiveViewWaitUntilEffective();
				String body = IOUtils.toString(request.getInputStream(), StandardCharsets.UTF_8);
				Map<String, String> config = null;
				if (body != null && body.length() > 0) {
					Map<?, ?> configMap = GSON.fromJson(body, Map.class);
					config = new HashMap<String, String>();
					for (Map.Entry<?, ?> entry : configMap.entrySet()) {
						config.put(entry.getKey().toString(), entry.getValue().toString());
					}
				}
				String[] fileNames = capture(response, request.getParameter("camera"), Boolean.valueOf(request.getParameter("download")),
						Boolean.valueOf(request.getParameter("downloadPreview")), config);
				if (fileNames != null) {
					Map<String, String> responseData = new HashMap<String, String>();
					if (fileNames[0] != null) {
						responseData.put("fileName", fileNames[0]);
					}
					if (fileNames[1] != null) {
						responseData.put("previewFileName", fileNames[1]);
					}
					writeJson(GSON, responseData, response);
				}
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
				Map<String, Object> result = new HashMap<String, Object>();
				synchronized (this) {
					GP2Context context = new GP2Context();
					List<CameraListItemBean> detectedCameras = GP2AutodetectCameraHelper.autodetectCameras(context);
					result.put("cameras", detectedCameras);
				}
				writeJson(GSON, result, response);
			} else if (requestPath.equals("/liveView")) {
				String cameraParam = request.getParameter("camera");
				System.out.println("Requested live view for " + cameraParam);
				GP2Camera camera = null;
				stopLiveViewWaitUntilEffective();
				if (LIVE_VIEW_IN_PROGRESS.compareAndSet(false, true)) {
					try {
						camera = getCameraInstanceForPort(cameraParam);

						if (camera != null) {
							response.setContentType("multipart/x-mixed-replace; boundary=--BoundaryString");
							final OutputStream outputStream = response.getOutputStream();
							byte[] jpeg;
							System.out.println("Serving live view for " + cameraParam);
							liveViewEnabled.set(true);
							while (liveViewEnabled.get()) {
								try {
									synchronized (this) {
										jpeg = camera.capturePreview();
									}
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
						} else {
							response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
						}
					} finally {
						System.out.println("Stopping live view for " + cameraParam);
						closeQuietly(camera);
						System.out.println("Stopped live view for " + cameraParam);
						LIVE_VIEW_IN_PROGRESS.set(false);
					}
				} else {
					response.setStatus(HttpServletResponse.SC_CONFLICT);
				}

			} else {
				response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	protected String[] capture(HttpServletResponse response, String port, boolean download, boolean downloadPreview, Map<String, String> config) {
		String[] result = new String[2];
		GP2Camera camera = null;
		try {
			camera = getCameraInstanceForPort(port);

			if (camera != null) {
				if (config != null && config.size() > 0) {
					List<CameraConfigEntryBean> cfg = GP2ConfigHelper.getConfig(camera);
					Map<String, CameraConfigEntryBean> configAsMap = new HashMap<String, CameraConfigEntryBean>();
					for (CameraConfigEntryBean cb : cfg) {
						configAsMap.put(cb.getPath(), cb);
					}
					List<CameraConfigEntryBean> modifiedConfigs = new ArrayList<CameraConfigEntryBean>();
					for (Map.Entry<String, String> configProp : config.entrySet()) {
						CameraConfigEntryBean confBean = configAsMap.get(configProp.getKey());
						if (confBean != null) {
							switch (confBean.getType().getValueType()) {
								case FLOAT:
									confBean = confBean.cloneWithNewValue(Float.parseFloat(configProp.getValue()));
								case INT:
									confBean = confBean.cloneWithNewValue(Integer.parseInt(configProp.getValue()));
								case STRING:
								default:
									confBean = confBean.cloneWithNewValue(configProp.getValue());
							}
							modifiedConfigs.add(confBean);
						}
					}
					if (modifiedConfigs.size() > 0) {
						GP2ConfigHelper.setConfig(camera, modifiedConfigs.toArray(new CameraConfigEntryBean[modifiedConfigs.size()]));
					}
				}

				synchronized (this) {
					CameraFileSystemEntryBean capturedFile = camera.capture();
					if (capturedFile != null) {
						if (download) {
							byte[] content = GP2CameraFilesHelper.getCameraFileContents(camera, capturedFile.getPath(), capturedFile.getName());
							File targetFile = new File(imagesDownloadFolder, capturedFile.getName());
							String targetFilePath = capturedFile.getName();
							try {
								targetFilePath = targetFile.getCanonicalPath();
								FileUtils.writeByteArrayToFile(targetFile, content, false);
								result[0] = capturedFile.getName();
							} catch (IOException e) {
								System.err.println("Error saving file " + targetFilePath);
								e.printStackTrace();
							}
						}
						if (downloadPreview) {
							byte[] content = GP2CameraFilesHelper.getCameraFileContents(camera, capturedFile.getPath(), capturedFile.getName(), true);
							String fileName = "preview_" + capturedFile.getName();
							File targetFile = new File(imagesDownloadFolder, fileName);
							String targetFilePath = fileName;
							try {
								targetFilePath = targetFile.getCanonicalPath();
								FileUtils.writeByteArrayToFile(targetFile, content, false);
								result[1] = fileName;
							} catch (IOException e) {
								System.err.println("Error saving file " + targetFilePath);
								e.printStackTrace();
							}
						}
					}
				}
			} else {
				response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
			}
		} finally {
			closeQuietly(camera);
		}
		return result;
	}

	protected synchronized GP2Camera getCameraInstanceForPort(String port) {
		GP2Camera camera = null;
		synchronized (this) {
			GP2PortInfoList portInfoList = new GP2PortInfoList();
			try {
				camera = new GP2Camera(portInfoList.getByPath(port));
			} catch (GP2Exception e) {
				if (e.getCode() == Gphoto2Library.GP_ERROR_CAMERA_BUSY) {
					System.err.println("Can't access camera: camera busy");
				} else if (e.getCode() == Gphoto2Library.GP_ERROR_IO_USB_CLAIM) {
					System.err.println("Can't access camera: USB busy");
				} else if (e.getCode() == Gphoto2Library.GP_ERROR_NOT_SUPPORTED) {
					System.err.println("Can't access camera: not supported");
				} else {
					System.err.println("Can't access camera: camera error " + e.getMessage());
				}
			} catch (Exception e) {
				camera = null;
				e.printStackTrace();
			} finally {
				if (portInfoList != null) {
					try {
						portInfoList.close();
					} catch (Exception e) {
					}
				}
			}
		}

		return camera;
	}

	protected void closeQuietly(GP2Camera camera) {
		if (camera != null) {
			try {
				synchronized (this) {
					List<CameraConfigEntryBean> cfg = GP2ConfigHelper.getConfig(camera);
					for (CameraConfigEntryBean cb : cfg) {
						if (cb.getPath().toLowerCase().contains("viewfinder") && cb.getType() == CameraConfigEntryType.TOGGLE) {
							GP2ConfigHelper.setConfig(camera, cb.cloneWithNewValue(0));
						}
					}
					camera.release();
					camera.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
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

	protected static void writeJson(Gson GSON, Object object, HttpServletResponse response) throws IOException {
		response.setContentType("application/json");
		GSON.toJson(object, response.getWriter());
	}
}
