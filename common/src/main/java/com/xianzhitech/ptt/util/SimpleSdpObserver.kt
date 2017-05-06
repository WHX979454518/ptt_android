package com.xianzhitech.ptt.util

import org.slf4j.LoggerFactory
import org.webrtc.*

open class SimpleSdpObserver : SdpObserver {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun onSetFailure(p0: String?) {
        logger.info("onSetFailure: {}", p0)
    }

    override fun onSetSuccess() {
        logger.info("onSetSuccess")
    }

    override fun onCreateSuccess(p0: SessionDescription) {
        logger.info("onCreateSuccess: {}", p0)
    }

    override fun onCreateFailure(p0: String?) {
        logger.info("onCreateFailure: {}", p0)
    }
}

open class SimplePeerConnectionObserver : PeerConnection.Observer {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun onIceCandidate(iceCandidate: IceCandidate) {
        logger.info("onIceCandidate: {}", iceCandidate)
    }

    override fun onDataChannel(p0: DataChannel?) {
        logger.info("onDataChannel: {}", p0)
    }

    override fun onIceConnectionReceivingChange(p0: Boolean) {
        logger.info("onIceConnectionReceivingChange: {}", p0)
    }

    override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
        logger.info("onIceConnectionChange: {}", p0)
    }

    override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
        logger.info("onIceGatheringChange: {}", p0)
    }

    override fun onAddStream(p0: MediaStream) {
        logger.info("onAddStream: {}", p0)
    }

    override fun onSignalingChange(p0: PeerConnection.SignalingState?) {
        logger.info("onSignalingChange: {}", p0)
    }

    override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {
        logger.info("onIceCandidatesRemoved: {}", p0)
    }

    override fun onRemoveStream(p0: MediaStream) {
        logger.info("onRemoveStream: {}", p0)
    }

    override fun onRenegotiationNeeded() {
        logger.info("onRenegotiationNeeded")
    }

    override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
        logger.info("onAddTrack: {}", p0)
    }
}

open class SimpleCameraEventsHandler : CameraVideoCapturer.CameraEventsHandler {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun onCameraError(p0: String?) {
        logger.info("onCameraError: {}", p0)
    }

    override fun onCameraOpening(p0: String?) {
        logger.info("onCameraOpening: {}", p0)
    }

    override fun onCameraDisconnected() {
        logger.info("onCameraDisconnected")
    }

    override fun onCameraFreezed(p0: String?) {
        logger.info("onCameraFreezed: {}", p0)
    }

    override fun onFirstFrameAvailable() {
        logger.info("onFirstFrameAvailable")
    }

    override fun onCameraClosed() {
        logger.info("onCameraClosed")
    }
}