package de.raimundwege.camera;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;

public class Camera extends ApplicationAdapter {

	private final DeviceCameraController deviceCameraController;

	public Camera(DeviceCameraController deviceCameraController) {
		this.deviceCameraController = deviceCameraController;
	}

	@Override
	public void create() {
		deviceCameraController.init();
	}

	@Override
	public void render() {
		Gdx.gl.glClearColor(0.7f, 0.7f, 0.7f, 1.0f);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

		deviceCameraController.render();
	}

}
