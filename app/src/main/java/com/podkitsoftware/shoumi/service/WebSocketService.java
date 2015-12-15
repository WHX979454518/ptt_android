package com.podkitsoftware.shoumi.service;

import android.util.Base64;

import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.podkitsoftware.shoumi.Broker;
import com.podkitsoftware.shoumi.model.Group;
import com.podkitsoftware.shoumi.model.GroupMember;
import com.podkitsoftware.shoumi.model.Person;
import com.podkitsoftware.shoumi.service.auth.IAuthService;
import com.podkitsoftware.shoumi.service.signal.ISignalService;
import com.podkitsoftware.shoumi.service.signal.Room;
import com.podkitsoftware.shoumi.service.sync.ISyncService;
import com.podkitsoftware.shoumi.util.JsonUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import io.socket.client.IO;
import io.socket.client.Manager;
import io.socket.client.Socket;
import io.socket.engineio.client.Transport;
import rx.Observable;
import rx.subjects.PublishSubject;

/**
 *
 * 基于WebSocket的信号服务器
 *
 * Created by fanchao on 13/12/15.
 */
public class WebSocketService implements ISignalService, IAuthService, ISyncService {
    private final String endpointUrl;
    private final int endpointPort;
    private Socket socket;
    final PublishSubject<Event> eventSubject = PublishSubject.create();
    final Broker broker;

    public static final String EVENT_USER_LOGON = "userLogon";
    public static final String EVENT_CONTACTS_UPDATE = "contactsUpdate";

    public WebSocketService(final Broker broker, final String endpointUrl, final int endpointPort) {
        this.broker = broker;
        this.endpointUrl = endpointUrl;
        this.endpointPort = endpointPort;

        eventSubject
                .filter(event -> EVENT_CONTACTS_UPDATE.equals(event.name))
                .flatMap(event -> {
                    final JSONObject object = (JSONObject) event.args[0];
                    final Person person = new Person();
                    final Group group = new Group();
                    try {
                        //TODO: Version control
                        final JSONArray groupJsonArray = object.getJSONObject("enterpriseGroups").getJSONArray("add");
                        final Iterable<Group> groups = JsonUtil.<JSONObject, Group>fromArray(groupJsonArray, group::readFrom);
                        final Iterable<Person> persons = JsonUtil.<JSONObject, Person>fromArray(object.getJSONObject("enterpriseMembers").getJSONArray("add"), person::readFrom);

                        return Observable.concat(
                                broker.updatePersons(persons),
                                broker.updateGroups(groups, GroupMember.fromJson(groupJsonArray)),
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
            transport.on(Transport.EVENT_REQUEST_HEADERS, transportArgs -> ((Map<String, String>) transportArgs[0]).put("Authorization",
                    new String(Base64.encode((username + ":" + password).getBytes(Charsets.UTF_8), Base64.NO_WRAP), Charsets.UTF_8)));
        });

        socket.on(Socket.EVENT_CONNECT_ERROR, new EventListener(Socket.EVENT_CONNECT_ERROR))
                .on(Socket.EVENT_CONNECT_TIMEOUT, new EventListener(Socket.EVENT_CONNECT_TIMEOUT));

        return eventSubject
                .flatMap(event -> {
                    if (Socket.EVENT_CONNECT_ERROR.equals(event.name)) {
                        return Observable.error(new RuntimeException("Connection error: " + event.args[0]));
                    } else if (Socket.EVENT_CONNECT_TIMEOUT.equals(event.name)) {
                        return Observable.error(new TimeoutException());
                    } else if (EVENT_USER_LOGON.equals(event.name)) {
                        return Observable.just(new Person().readFrom((JSONObject) event.args[0]));
                    }

                    return Observable.empty();
                });
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
