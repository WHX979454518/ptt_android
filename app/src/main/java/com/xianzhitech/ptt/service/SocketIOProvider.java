package com.xianzhitech.ptt.service;

import android.support.annotation.NonNull;
import android.util.Base64;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.xianzhitech.model.Group;
import com.xianzhitech.model.Person;
import com.xianzhitech.ptt.Broker;
import com.xianzhitech.ptt.service.auth.IAuthService;
import com.xianzhitech.ptt.service.signal.Room;
import com.xianzhitech.ptt.service.signal.SignalProvider;
import com.xianzhitech.ptt.service.sync.ISyncService;
import com.xianzhitech.ptt.util.JsonUtil;
import io.socket.client.IO;
import io.socket.client.Manager;
import io.socket.client.Socket;
import io.socket.engineio.client.Transport;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import rx.Observable;
import rx.subjects.PublishSubject;

import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 *
 * 基于WebSocket的信号服务器
 *
 * Created by fanchao on 13/12/15.
 */
public class SocketIOProvider implements SignalProvider, IAuthService, ISyncService {
    private final String endpointUrl;
    private Socket socket;
    final PublishSubject<Event> eventSubject = PublishSubject.create();
    final Broker broker;

    public static final String EVENT_USER_LOGON = "userLogon";
    public static final String EVENT_CONTACTS_UPDATE = "contactsUpdate";

    public SocketIOProvider(final Broker broker, final String endpointUrl) {
        this.broker = broker;
        this.endpointUrl = endpointUrl;

        eventSubject
                .filter(event -> EVENT_CONTACTS_UPDATE.equals(event.name))
                .flatMap(event -> {
                    final JSONObject object = (JSONObject) event.args[0];
                    final Person person = new Person();
                    final Group group = new Group();
                    try {
                        //TODO: Version control
                        final JSONArray groupJsonArray = object.getJSONObject("enterpriseGroups").getJSONArray("add");
                        final Iterable<Group> groups = JsonUtil.<JSONObject, Group>fromArray(groupJsonArray, group::from);
                        final Iterable<Person> persons = JsonUtil.<JSONObject, Person>fromArray(object.getJSONObject("enterpriseMembers").getJSONArray("add"), person::readFrom);

                        return Observable.concat(
                                broker.updatePersons(persons),
                                broker.updateGroups(groups, GroupMembers.fromJson(groupJsonArray)),
                                broker.updateContacts(Iterables.transform(persons, Person::getId), Iterables.transform(groups, Group::getId))
                        );
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                })

                .subscribe();
    }

    @Override
    public Room joinRoom(final String groupId) {
        return null;
    }

    @Override
    public void quitRoom(final String groupId) {
    }

    @Override
    public boolean requestFocus(final int roomId) {
        return false;
    }

    @Override
    public void releaseFocus(final int roomId) {

    }

    @Override
    public void stopSync() {

    }

    @Override
    public Observable<Person> login(final String username, final String password) {
        if (socket != null) {
            throw new IllegalStateException("Already authorized");
        }

        try {
            socket = IO.socket(endpointUrl);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        socket.io().on(Manager.EVENT_TRANSPORT, args -> {
            final Transport transport = (Transport) args[0];
            transport.on(Transport.EVENT_REQUEST_HEADERS, transportArgs -> ((Map<String, List<String>>) transportArgs[0]).put("Authorization",
                    Collections.singletonList("Basic " + encodeCredentials(username, password))));
        });

        socket.on(Socket.EVENT_CONNECT_ERROR, new EventListener(Socket.EVENT_CONNECT_ERROR))
                .on(Socket.EVENT_CONNECT, new EventListener(Socket.EVENT_CONNECT))
                .on(Socket.EVENT_ERROR, new EventListener(Socket.EVENT_ERROR))
                .on(Socket.EVENT_CONNECT_TIMEOUT, new EventListener(Socket.EVENT_CONNECT_TIMEOUT))
                .on(EVENT_USER_LOGON, new EventListener(EVENT_USER_LOGON))
                .on(EVENT_CONTACTS_UPDATE, new EventListener(EVENT_CONTACTS_UPDATE));

        return eventSubject
                .flatMap(event -> {
                    if (Socket.EVENT_CONNECT_ERROR.equals(event.name) || Socket.EVENT_ERROR.equals(event.name)) {
                        return Observable.error(new RuntimeException("Connection error: " + event.args[0]));
                    } else if (Socket.EVENT_CONNECT_TIMEOUT.equals(event.name)) {
                        return Observable.error(new TimeoutException());
                    } else if (EVENT_USER_LOGON.equals(event.name)) {
                        return Observable.just(new Person().readFrom((JSONObject) event.args[0]));
                    } else if (Socket.EVENT_CONNECT.equals(event.name)) {
                        socket.emit("syncContacts", new JSONObject(ImmutableMap.of("enterMemberVersion", 1, "enterGroupVersion", 1)));
                    }

                    return Observable.empty();
                })
                .doOnSubscribe(socket::connect);
    }

    @NonNull
    private String encodeCredentials(final String username, final String password) {
        return Base64.encodeToString((username + ":" + MD5(password)).getBytes(), Base64.NO_WRAP);
    }

    private static String MD5(String md5) {
        try {
            MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] array = md.digest(md5.getBytes());
            final StringBuilder sb = new StringBuilder();
            for (final byte b : array) {
                sb.append(Integer.toHexString((b & 0xFF) | 0x100).substring(1, 3));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void logout() {
        if (socket != null) {
            socket.close();
            socket = null;
        }
    }

    private class EventListener implements Socket.Listener {
        private final String eventName;

        private EventListener(final String eventName) {
            this.eventName = eventName;
        }

        @Override
        public void call(final Object... args) {
            eventSubject.onNext(new Event(eventName, args));
        }
    }

    private static class Event {
        public final String name;
        public final Object[] args;

        private Event(final String name, final Object[] args) {
            this.name = name;
            this.args = args;
        }
    }
}
