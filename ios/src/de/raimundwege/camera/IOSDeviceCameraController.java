package de.raimundwege.camera;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.robovm.apple.avfoundation.AVCaptureConnection;
import org.robovm.apple.avfoundation.AVCaptureDevice;
import org.robovm.apple.avfoundation.AVCaptureDeviceInput;
import org.robovm.apple.avfoundation.AVCaptureOutput;
import org.robovm.apple.avfoundation.AVCaptureSession;
import org.robovm.apple.avfoundation.AVCaptureSessionPreset;
import org.robovm.apple.avfoundation.AVCaptureVideoDataOutput;
import org.robovm.apple.avfoundation.AVCaptureVideoDataOutputSampleBufferDelegate;
import org.robovm.apple.avfoundation.AVPixelBufferAttributes;
import org.robovm.apple.coremedia.CMSampleBuffer;
import org.robovm.apple.corevideo.CVImageBuffer;
import org.robovm.apple.corevideo.CVPixelFormatType;
import org.robovm.apple.corevideo.CVReturn;
import org.robovm.apple.dispatch.DispatchQueue;
import org.robovm.apple.dispatch.DispatchQueueAttr;
import org.robovm.apple.foundation.NSAutoreleasePool;
import org.robovm.apple.foundation.NSErrorException;
import org.robovm.apple.foundation.NSObject;
import org.robovm.rt.VM;
import org.robovm.rt.bro.annotation.Bridge;
import org.robovm.rt.bro.annotation.Library;
import org.robovm.rt.bro.annotation.Pointer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

@Library("CoreMedia")
public class IOSDeviceCameraController extends NSObject implements
		DeviceCameraController, AVCaptureVideoDataOutputSampleBufferDelegate {

	public static int GL_BGRA = 0x80E1;

	private ShaderProgram shader;
	private Texture texture;
	private Mesh mesh;
	private ByteBuffer cameraBuffer;
	private int cameraWidth;
	private int cameraHeight;

	// Lock
	private final Lock bufferLock = new ReentrantLock();
	private boolean bufferProcessing;

	@Override
	public void init() {
		initCamera();
		initOpenGL();
	}

	private void initCamera() {

		// Device
		AVCaptureDevice device = AVCaptureDevice.getDevices().first();
		System.out.println(device.description());

		// Capture input
		AVCaptureDeviceInput captureInput = null;
		try {
			captureInput = new AVCaptureDeviceInput(device);
		} catch (NSErrorException e) {
			e.printStackTrace();
		}
		if (captureInput == null) {
			System.out.println("init capture device failed");
			return;
		}

		// Capture output
		AVCaptureVideoDataOutput captureOutput = new AVCaptureVideoDataOutput();
		captureOutput.setAlwaysDiscardsLateVideoFrames(true);

		// Queue
		DispatchQueue queue = DispatchQueue.create("cameraQueue",
				DispatchQueueAttr.Serial());
		queue.setTargetQueue(DispatchQueue.getGlobalQueue(
				DispatchQueue.PRIORITY_HIGH, 0));
		captureOutput.setSampleBufferDelegate(this, queue);
		queue.release();

		// Capture output settings
		AVPixelBufferAttributes pixelBufferAttributes = new AVPixelBufferAttributes();
		pixelBufferAttributes.setPixelFormatType(CVPixelFormatType._32BGRA);
		captureOutput.setPixelBufferSettings(pixelBufferAttributes);

		// Capture session
		AVCaptureSession captureSession = new AVCaptureSession();
		captureSession.addInput(captureInput);
		captureSession.addOutput(captureOutput);
		captureSession.setSessionPreset(AVCaptureSessionPreset.High);
		captureSession.startRunning();
	}

	private void initOpenGL() {

		// Create and compile the shader
		shader = new ShaderProgram(
				Gdx.files.internal("shaders/camera/ios.vsh"),
				Gdx.files.internal("shaders/camera/ios.fsh"));

		// Create the mesh to draw the textures on
		mesh = new Mesh(true, 4, 6, new VertexAttribute(Usage.Position, 2,
				"a_position"), new VertexAttribute(Usage.TextureCoordinates, 2,
				"a_texCoord"));

		// The vertices include the screen coordinates (between -1.0 and 1.0)
		// and texture coordinates (between 0.0 and 1.0)
		float left = -1;
		float right = -left;
		float top = 1;
		float bottom = -top;
		float[] vertices = { left, top, // Position 0
				0.0f, 0.0f, // TexCoord 0
				left, bottom, // Position 1
				0.0f, 1.0f, // TexCoord 1
				right, bottom, // Position 2
				1.0f, 1.0f, // TexCoord 2
				right, top, // Position 3
				1.0f, 0.0f // TexCoord 3
		};

		// The indices for the mesh
		short[] indices = { 0, 1, 2, 0, 2, 3 };

		// Set vertices and indices to the mesh
		mesh.setVertices(vertices);
		mesh.setIndices(indices);
	}

	@Override
	public void render() {
		if (cameraBuffer != null) {
			bufferLock.lock();

			// Set texture slot 0 as active and bind the texture object to
			// it
			Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
			texture.bind();

			// Link camera buffer
			Gdx.gl.glTexImage2D(GL20.GL_TEXTURE_2D, 0, GL20.GL_RGBA,
					cameraWidth, cameraHeight, 0, GL_BGRA,
					GL20.GL_UNSIGNED_BYTE, cameraBuffer);

			// Use linear interpolation when magnifying/minifying the
			// texture to
			// areas larger/smaller than the texture size
			Gdx.gl.glTexParameterf(GL20.GL_TEXTURE_2D,
					GL20.GL_TEXTURE_MIN_FILTER, GL20.GL_LINEAR);
			Gdx.gl.glTexParameterf(GL20.GL_TEXTURE_2D,
					GL20.GL_TEXTURE_MAG_FILTER, GL20.GL_LINEAR);
			Gdx.gl.glTexParameterf(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_S,
					GL20.GL_CLAMP_TO_EDGE);
			Gdx.gl.glTexParameterf(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_T,
					GL20.GL_CLAMP_TO_EDGE);

			// Render the texture
			shader.begin();
			shader.setUniformi("u_texture", 0);
			mesh.render(shader, GL20.GL_TRIANGLES);
			shader.end();

			bufferLock.unlock();
		}
	}

	@Bridge
	private static native CVImageBuffer CMSampleBufferGetImageBuffer(
			CMSampleBuffer sampleBuffer);

	@Bridge
	private static native @Pointer long CVPixelBufferGetBaseAddress(
			CVImageBuffer imageBuffer);

	@Bridge
	private static native CVReturn CVPixelBufferLockBaseAddress(
			CVImageBuffer imageBuffer, long optionFlags);

	@Bridge
	private static native CVReturn CVPixelBufferUnlockBaseAddress(
			CVImageBuffer imageBuffer, long optionFlags);

	@Bridge
	private static native long CVPixelBufferGetWidth(CVImageBuffer imageBuffer);

	@Bridge
	private static native long CVPixelBufferGetHeight(CVImageBuffer imageBuffer);

	@Bridge
	private static native long CVPixelBufferGetBytesPerRow(
			CVImageBuffer imageBuffer);

	@Override
	public void didOutputSampleBuffer(AVCaptureOutput captureOutput,
			CMSampleBuffer sampleBuffer, AVCaptureConnection connection) {
		NSAutoreleasePool pool = new NSAutoreleasePool();

		// Get image buffer and lock base address
		CVImageBuffer imageBuffer = CMSampleBufferGetImageBuffer(sampleBuffer);
		CVPixelBufferLockBaseAddress(imageBuffer, 0);

		// Get image buffer informations
		final int baseAddress = (int) CVPixelBufferGetBaseAddress(imageBuffer);
		final long width = getUnsignedLong(CVPixelBufferGetWidth(imageBuffer));
		final long height = getUnsignedLong(CVPixelBufferGetHeight(imageBuffer));
		final long bytesPerRow = getUnsignedLong(CVPixelBufferGetBytesPerRow(imageBuffer));
		final long bytesPerPixel = bytesPerRow / width;
		final long size = width * height * bytesPerPixel;

		// Copy buffer into camera buffer
		bufferLock.lock();
		ByteBuffer buffer = VM.newDirectByteBuffer(baseAddress, size);
		cameraWidth = (int) width;
		cameraHeight = (int) height;
		if (cameraBuffer == null
				|| cameraBuffer.capacity() != buffer.capacity()) {
			cameraBuffer = ByteBuffer.allocateDirect(buffer.capacity());
			cameraBuffer.order(ByteOrder.nativeOrder());
			texture = new Texture(cameraWidth, cameraHeight, Format.RGBA8888);
		}
		cameraBuffer.position(0);
		cameraBuffer.put(buffer);
		cameraBuffer.position(0);
		bufferLock.unlock();

		// Unlock base address
		CVPixelBufferUnlockBaseAddress(imageBuffer, 0);

		// Release buffer
		imageBuffer.release();
		sampleBuffer.release();

		pool.close();
	}

	@Override
	public void didDropSampleBuffer(AVCaptureOutput captureOutput,
			CMSampleBuffer sampleBuffer, AVCaptureConnection connection) {
		System.out.println("drop");
	}

	private static long getUnsignedLong(long x) {
		return x & 0xffffffffL;
	}

}
