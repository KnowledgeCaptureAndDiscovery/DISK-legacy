package org.diskproject.client.components.brain;

import com.github.gwtd3.api.arrays.Array;
import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.ScriptInjector;

import com.google.gwt.event.logical.shared.ResizeEvent;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.typedarrays.client.JsUtils;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Widget;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.diskproject.client.Config;
import org.diskproject.client.rest.StaticREST;
import org.treblereel.gwt.three4g.THREE;
import org.treblereel.gwt.three4g.cameras.PerspectiveCamera;
import org.treblereel.gwt.three4g.core.AbstractGeometry;
import org.treblereel.gwt.three4g.core.BufferGeometry;
import org.treblereel.gwt.three4g.core.Face3;
import org.treblereel.gwt.three4g.core.Geometry;
import org.treblereel.gwt.three4g.extensions.controls.TrackballControls;
import org.treblereel.gwt.three4g.extensions.quickhull.Face;
import org.treblereel.gwt.three4g.extensions.resources.TK_3JSResourceUtils;
import org.treblereel.gwt.three4g.lights.DirectionalLight;
import org.treblereel.gwt.three4g.loaders.BufferGeometryLoader;
import org.treblereel.gwt.three4g.loaders.ObjectLoader;
import org.treblereel.gwt.three4g.materials.MeshLambertMaterial;
import org.treblereel.gwt.three4g.materials.parameters.MeshLambertMaterialParameters;
import org.treblereel.gwt.three4g.objects.Mesh;
import org.treblereel.gwt.three4g.renderers.WebGLRenderer;
import org.treblereel.gwt.three4g.renderers.parameters.WebGLRendererParameters;
import org.treblereel.gwt.three4g.scenes.Scene;

import elemental2.core.JsArray;
import elemental2.dom.DOMRect;
import elemental2.dom.Event;
import elemental2.dom.EventListener;
import elemental2.dom.HTMLCanvasElement;

public class Brain extends Composite {
  interface Binder extends UiBinder<Widget, Brain> {};
  private static Binder uiBinder = GWT.create(Binder.class);
  
  private static boolean threeLoaded = false;
  
  @UiField HTMLCanvasElement canvas;
  
  private PerspectiveCamera camera;
  private TrackballControls controls;
  private WebGLRenderer renderer;
  private Scene scene;
  
  //TODO: cur_picked = null;

  private String manifest_url; // "filestoload_all.php"; TODO
  private String data_url;
  
  private Map<String, Mesh> meshes;
  private Map<String, MeshProperties> meshProperties;


  public Brain() {
	  // See https://github.com/treblereel/three4g/issues/155
	  if (!Brain.threeLoaded) {
		  ScriptInjector.fromString(TK_3JSResourceUtils.IMPL.getTrackballControls().getText()).setWindow(ScriptInjector.TOP_WINDOW).inject();
		  Brain.threeLoaded = false;
	  }

	  initWidget(uiBinder.createAndBindUi(this)); 
	  initialize();
  }
  
  private void initialize () {
	  DOMRect sz = canvas.getBoundingClientRect();
	  
	  // Set manifest_url
	  manifest_url = Config.getServerURL() + "/public/files.json";
	  GWT.log("BRAIN VIZ INIT: " + manifest_url);

	  //Some important variables
	  this.meshes = new HashMap<String, Mesh>();
	  this.meshProperties = new HashMap<String, MeshProperties>();

	  // The Camera
	  // Params: x,y,z starting position
	  this.camera = new PerspectiveCamera((float) 50, (float) (sz.width/sz.height), (float) 0.1, (float) 1e10);
	  this.camera.position.z = 200;
	  
	  GWT.log("Camera: ");
	  this.camera.aspect = (float) 1.5;
	  nativeLog(this.camera);

	  // The Renderer
	  WebGLRendererParameters renderParams = new WebGLRendererParameters();
	  renderParams.antialias = true;
	  renderParams.alpha = true;
	  this.renderer = new WebGLRenderer( renderParams );
	  //this.renderer.setPixelRatio( window.devicePixelRatio );
	  this.renderer.setSize( sz.width, sz.height );

	  // The Controls
	  // Params: None. Just add the camera to controls
	  this.controls = this.addControls(this.camera);

	  // The Scene
	  // Params: None. Just add the camera to the scene
	  this.scene = new Scene();
	  this.scene.add( this.camera );

	  // The Lights!
	  // Params: None for now... add to camera
	  this.addLights(this.camera);

	  // The Mesh
	  // Params: None for now... add to scene
	  this.loadBrain();

	  // The spot in the HTML
	  //this.container.appendChild( this.renderer.domElement );
	  this.animate();
	  Timer t = new Timer() {
		  @Override
		  public void run() {
			  // TODO Auto-generated method stub
			  animate();
		  }
	  };
	  //t.schedule(1000);

	  Window.addResizeHandler(new ResizeHandler() {
		  @Override
		  public void onResize(ResizeEvent event) {
			  onWindowResize();
		  }
	  });

	  canvas.addEventListener("click", new EventListener() {
		  @Override
		  public void handleEvent(Event evt) {
			  onClick(evt);
		  }
	  });
  }

  private TrackballControls addControls (PerspectiveCamera camera){
	  GWT.log("Adding controls");
	  controls = new TrackballControls(camera, this.canvas);

	  controls.rotateSpeed = (float) 5.0;
	  controls.zoomSpeed = 5;
	  controls.panSpeed = 2;

	  controls.noZoom = false;
	  controls.noPan = false;

	  controls.staticMoving = true;
	  controls.dynamicDampingFactor = (float) 0.3;
	  return controls;
  }

  private void addLights (PerspectiveCamera camera){
	  GWT.log("Adding lights");
	  DirectionalLight dirLight = new DirectionalLight( 0xffffff );
	  dirLight.position.set( 200, 200, 1000 ).normalize();

	  camera.add( dirLight );
	  camera.add( dirLight.target );
  }

  private void animate () {
	  GWT.log("- Animated -");
	  /*setTimeout( function() {
		  requestAnimationFrame( function() { _this.animate(); });
	  }, 1000/12);*/
	  
	  this.controls.update();
	  this.renderer.render( this.scene, this.camera );
	  nativeLog(this.scene);
  }

  private void onWindowResize () {
	  GWT.log("On resize");
	  DOMRect sz = this.canvas.getBoundingClientRect();
	  this.camera.aspect = (float) (sz.width / sz.height);
	  this.camera.updateProjectionMatrix();

	  this.renderer.setSize( sz.width, sz.height );
	  this.renderer.setClearColor(0xffffff, 1);

	  //this.controls.handleResize();
  }

  private void onClick (Event e) {
	  GWT.log("On click!");
	  animate();
	  /*if (!e.shiftKey)
		  return false;

	  if (_this.onselect) {
		  //_this.decreaseOpacityAll();
		  mesh = _this.selectMeshByMouse(e);
		  _this.increaseOpacity(mesh); 
		  _this.onselect(mesh);
	  }*/
  }

  private void loadBrain () {
	  GWT.log("loading brain... ");
	  if (this.manifest_url == null || this.manifest_url.equals(""))
		  return;
	  /*if (this.label_mapper === null) TODO: I dont understand this
		  return;*/

	  StaticREST.getJSObject(manifest_url, new Callback<JavaScriptObject, Throwable>() {
		  @Override
		  public void onSuccess(JavaScriptObject result) {
			  // TODO Auto-generated method stub
			  reset_mesh_props(result, true);
		  }
		  @Override
		  public void onFailure(Throwable reason) {
			  // TODO Auto-generated method stub
		  }
	  });
	  /*console.log(_this.manifest_url); Should be REST i guess
	  $.ajax({dataType: "json",
		  url: _this.manifest_url,
		  data: function(data) {},
		  error: function(jqXHR, textStatus, errorThrown) { console.error(textStatus, errorThrown); },
		  success: function(data, textStatus, jqXHR) {
			  reset_mesh_props(data, true);
		  }
	  });*/
  }

  private void clearBrain (String[] keeper_roi_keys) {
	  GWT.log("clearing brain but keeping " + keeper_roi_keys.length + " rois");
	  if (keeper_roi_keys != null && keeper_roi_keys.length > 0) {
		  for (String key: meshes.keySet()) {
			  if (Arrays.stream(keeper_roi_keys).anyMatch(x -> x == key)) {
				  continue;
			  }
			  removeMesh(key);
		  }
	  }
	  //for (String key: keeper_roi_keys)
		//  GWT.log("+ " + key);
  }
  
  /*private float[] toFloatArray () {
	  return null {1.0 ,1.0, 1.0};
  }*/
  
  private float[] toFloatArray (String[] elements) {
	  float[] array = new float[3];
	  for (int i = 0; i < 3; i++) {
		  array[i] = Float.parseFloat(elements[i]);
	  }
	  return array;
  }

  private native String nativeGetPropertyValue (JavaScriptObject data, String prop_name, String key, String default_val) /*-{
  	var val = (prop_name in data) ? data[prop_name][key] : default_val;
  	//TODO: theres a static value_key here, not sure for what is used
  	return val;
  }-*/;

  private native String[] nativeGetStringArray (JavaScriptObject data, String prop_name, String key, String[] default_val) /*-{
  	var val = (prop_name in data) ? data[prop_name][key] : default_val;
  	//TODO: theres a static value_key here, not sure for what is used
  	return val;
  }-*/;
  
  private native void nativeLog (Object obj) /*-{
  	console.log(obj);
  }-*/;

  private void reset_mesh_props (JavaScriptObject data, boolean paint_colors) {
	  String[] keys = nativeGetKeySet(data);
	  String key0 = keys[0];
	  String[] roi_keys = nativeGetKeySet(nativeGet(data, key0));
	  clearBrain(roi_keys);
	  String base_url = manifest_url.substring(0, manifest_url.lastIndexOf('/'));
	  GWT.log("base_url: " + base_url);
	  
	  String[] baseColors = {"1", "1", "1"};
	  
	  for (String key: roi_keys) {
		  String mesh_url = nativeGetPropertyValue(data, "filename", key, null);
		  
		  MeshProperties meshProps = new MeshProperties(
			  nativeGetPropertyValue(data, "name", key, key),
			  toFloatArray(paint_colors ? nativeGetStringArray(data, "colors", key, null) : baseColors),
			  nativeGetPropertyValue(data, "values", key, null),
			  key);
		  
		  // Select the needed value
		  /*if (mesh.value && mesh.value.length ) //Casting something array -> elem ??
			  mesh.value = mesh.value[Object.keys(mesh_props.value)[0]];*/
		  //LOAD the 3D file!
		  
		  if (mesh_url != null && !mesh_url.equals("")) {  // Load remote mesh
			  if (mesh_url.charAt(0) != '/') // relative path is relative to manifest
				  mesh_url = base_url + "/models/" + mesh_url;
			  loadMesh(mesh_url, meshProps);
		  } else if (meshes.containsKey(meshProps.roi_key)) {  // Set existing mesh properties
			  //copy_mesh_props(mesh_props, _this.meshes[mesh_props.roi_key]);
		  } else {  // Didn't load mesh, none existing...
			  GWT.log("Mesh URL not specified for" + meshProps.roi_key+", no existing mesh, skipping...");
		  }
		  break; //FIXME
		  
	  }
  }

  private native JavaScriptObject nativeGet (JavaScriptObject data, String key) /*-{
  	return data[key];
  }-*/;

  private native String[] nativeGetKeySet (JavaScriptObject data) /*-{
  	return Object.keys(data);
  }-*/;

  private void removeMesh (String roi_key) {
	  //HACK?
	  meshes.remove(roi_key);
	  meshProperties.remove(roi_key);
  }

  private native BufferGeometry<BufferGeometry> VTKLoader (String buffer) /*-{
  	return new THREE.VTKLoader().parse(buffer);
  }-*/;

  private void loadMesh (String url, MeshProperties mesh_props) {
	  GWT.log("Trying to load " + url);
	  boolean found = meshProperties.containsKey(mesh_props.roi_key);
	  if (found && url.equals(meshProperties.get(mesh_props.roi_key).filename)) {
		  Mesh mesh = meshes.get(mesh_props.roi_key);
		  //copy_mesh_props(mesh_props, mesh);
	  } else {
		  if (found) {// Unreusable mesh; remove it
			  removeMesh(mesh_props.roi_key);
		  }
		  StaticREST.getAsString(url, new Callback<String, Throwable>() {
			  @Override
			  public void onSuccess(String result) {
				  //FIXME: Loading vtk files
				  //BufferGeometry<BufferGeometry> bufferGeometry = (BufferGeometry<BufferGeometry>) new BufferGeometryLoader().load(url);
				  //BufferGeometry<BufferGeometry> bufferGeometry = VTKLoader(result);
				  BufferGeometry<BufferGeometry> bufferGeometry = VTKParser.parse(result);

				  Geometry geometry = new Geometry().fromBufferGeometry(bufferGeometry);
				  geometry.computeFaceNormals();
				  geometry.computeVertexNormals();
				  //geometry.__dirtyColors = true;
				  
				  MeshLambertMaterialParameters materialParams = new MeshLambertMaterialParameters();
				  materialParams.vertexColors = THREE.FaceColors;
				  MeshLambertMaterial material = new MeshLambertMaterial(materialParams);
				  
				  //nativeLog(geometry);
				  
				  Mesh mesh = new Mesh(geometry, material);
				  copy_mesh_props(mesh_props, mesh);

				  mesh_props.filename = url;
				  //mesh.dynamic = true;
				  
				  mesh.material.transparent = true;
				  mesh.material.opacity = 1;
				  mesh.rotation.y = (float) (Math.PI * 1.01);
				  mesh.rotation.x = (float) (Math.PI * 0.5);
				  mesh.rotation.z = (float) (Math.PI * 1.5 * (url.indexOf("rh_") == -1 ? 1 : -1));
				  
				  if (mesh_props.name != null && !mesh_props.name.equals("")) {
					  mesh.name = mesh_props.name;
				  } else {
					  String tmp[] = url.split("_");
					  mesh.name = tmp[tmp.length-1].split(".vtk")[0];
				  }
				  
				  scene.add(mesh);
				  meshes.put(mesh_props.roi_key, mesh);
				  meshProperties.put(mesh_props.roi_key, mesh_props);
			  }
			  @Override
			  public void onFailure(Throwable reason) {
				  // TODO Auto-generated method stub

			  }
		  });
	  }
  }
  
  private void copy_mesh_props (MeshProperties meshProp, Mesh mesh) {
	  set_mesh_color(mesh, meshProp.color);
  }
  
  private void set_mesh_color (Mesh mesh, float[] color) {
	  Geometry geometry = (Geometry) mesh.geometry;
	  for (int i = geometry.faces.length -1; i >= 0; i--) {
		  Face3 face = geometry.faces.getAt(i);
		  if (color != null) {
			  face.color.setHex((int) (Math.random() * 0xffffff));
			  face.color.setRGB(color[0], color[1], color[2]);
		  } else {
			  JsArray<Face3> before_faces = geometry.faces.slice(0,i);
			  JsArray<Face3> after_faces = geometry.faces.slice(i+1, geometry.faces.length);
			  Face3[] after = new Face3[after_faces.length];
			  for (int j = 0; i < after_faces.length; i++) {
				  after[j] = after_faces.getAt(j);
			  }
			  geometry.faces = before_faces.concat(after);
		  }
	  }
	  geometry.colorsNeedUpdate = true;
  }
}