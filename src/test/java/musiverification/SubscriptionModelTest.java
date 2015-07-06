package musiverification;

import ddp.api.TestUtils;
import junit.framework.TestCase;
import net.openhft.chronicle.engine.api.map.MapEvent;
import net.openhft.chronicle.engine.api.map.MapEventListener;
import net.openhft.chronicle.engine.api.pubsub.InvalidSubscriberException;
import net.openhft.chronicle.engine.api.pubsub.Subscriber;
import net.openhft.chronicle.engine.api.pubsub.TopicSubscriber;
import net.openhft.chronicle.engine.api.tree.AssetTree;
import net.openhft.chronicle.engine.tree.AddedAssetEvent;
import net.openhft.chronicle.engine.tree.ExistingAssetEvent;
import net.openhft.chronicle.engine.tree.RemovedAssetEvent;
import net.openhft.chronicle.engine.tree.TopologicalEvent;
import org.junit.*;

import java.io.IOException;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.IntStream;

import static net.openhft.chronicle.engine.Chassis.*;
import static org.easymock.EasyMock.*;

public class SubscriptionModelTest {
    private static Map<String, String> _stringStringMap;
    private static String _mapName = "chronicleMapString";
    private static String _mapArgs = "putReturnsNull=true";
    private static AssetTree _clientAssetTree;

    //TODO DS all events should be asynchronous
    //TODO DS call back method must include map name (I don't think it does?), key name, value inserted - no longer necessary as you subscribe to a specific map

    @Before
    public void setUp() {
        resetChassis();

        _stringStringMap = acquireMap(String.format("%s?%s", _mapName, _mapArgs), String.class, String.class);
        _stringStringMap.clear();

        _clientAssetTree = assetTree();
    }

    @After
    public void tearDown() throws IOException {
        assetTree().close();
    }

    /**
     * Test subscribing to all MapEvents for a given map. Expect to receive events for insert,
     * update and remove actions for all keys. All events should be received in the order they are
     * executed.
     *
     * @
     */
    @Test
    @Ignore
    public void testSubscriptionMapEventOnAllKeys() {
        MapEventListener<String, String> mapEventListener = createStrictMock(MapEventListener.class);
        _clientAssetTree.registerSubscriber(_mapName, MapEvent.class, e -> e.apply(mapEventListener));

        int noOfKeys = 5;
        int noOfValues = 5;

        //Setup all the expected events in the correct order

        //Setup insert events for all keys
        iterateAndExecuteConsumer((k, v) -> mapEventListener.insert(_mapName, k, v), noOfKeys, _mapName, _mapName);

        //Setup update events for all keys
        for (int i = 0; i < noOfKeys * noOfValues; i++) {
            mapEventListener.update(_mapName, TestUtils.getKey(_mapName, i % noOfKeys), TestUtils.getValue(_mapName, i), TestUtils.getValue(_mapName, i + noOfKeys));
        }

        //Setup remove events for all keys
        iterateAndExecuteConsumer((k, v) -> mapEventListener.remove(_mapName, k, v), c -> c, c -> noOfKeys * noOfValues + c, noOfKeys, _mapName, _mapName);

        replay(mapEventListener);

        //Perform all initial puts (insert events)
        iterateAndExecuteConsumer((k, v) -> _stringStringMap.put(k, v), noOfKeys, _mapName, _mapName);

        //Perform all puts (update events)
        for (int i = 0; i < noOfKeys * noOfValues; i++) {
            _stringStringMap.put(TestUtils.getKey(_mapName, i % noOfKeys), TestUtils.getValue(_mapName, i + noOfKeys));
        }

        //Perform all remove (remove events)
        iterateAndExecuteConsumer((k, v) -> _stringStringMap.remove(k), noOfKeys, _mapName, _mapName);

        verify(mapEventListener);
    }

    /**
     * Test subscribing to updates on a specific key. Perform initial puts (insert). Perform more
     * puts (updates). Remove the key.
     *
     * @
     */
    @Test
    public void testSubscriptionSpecificKey() throws InvalidSubscriberException {
        //TODO DS connecting to a server based Java component using the clietn API can be notified by callback methods for specified key in a given map

        //TODO DS refactor to use mock (strict)
        String testKey = "Key-sub-1";

        Subscriber<String> testChronicleKeyEventSubscriber = createStrictMock(Subscriber.class);

        //Set up teh mock
        String update1 = "Update1";
        String update2 = "Update2";
        String update3 = "Update3";
        String update4 = "Update4";
        String update5 = "Update5";

        testChronicleKeyEventSubscriber.onMessage(update1);
        testChronicleKeyEventSubscriber.onMessage(update2);
        testChronicleKeyEventSubscriber.onMessage(update3);
        testChronicleKeyEventSubscriber.onMessage(update4);
        testChronicleKeyEventSubscriber.onMessage(update5);
        testChronicleKeyEventSubscriber.onMessage(null); //Key removed

        replay(testChronicleKeyEventSubscriber);

        //Setting bootstrap = false otherwise we would get an initial event with null
        _clientAssetTree.registerSubscriber(_mapName + "/" + testKey + "?bootstrap=false", String.class, testChronicleKeyEventSubscriber); //TODO DS do a test with boot strapping

        //Perform some puts and replace
        _stringStringMap.put(testKey, update1);
        _stringStringMap.put(testKey, update2);
        _stringStringMap.replace(testKey, update2, update3);
        _stringStringMap.put(testKey, update4);

        //Perform one put on test key and a number of operations on another key and check number of updater
        String irrelevantTestKey = "Key-nonsub";
        _stringStringMap.put(irrelevantTestKey, "RandomVal1");
        _stringStringMap.put(testKey, update5);
        _stringStringMap.put(irrelevantTestKey, "RandomVal2");
        _stringStringMap.replace(irrelevantTestKey, "RandomVal2", "RandomVal3");
        _stringStringMap.remove(irrelevantTestKey);

        //Remove the test key and test the number of updates
        _stringStringMap.remove(testKey);

        verify(testChronicleKeyEventSubscriber);

        // expect to be told when the tree is torn down.
        reset(testChronicleKeyEventSubscriber);
        testChronicleKeyEventSubscriber.onEndOfSubscription();
        replay(testChronicleKeyEventSubscriber);
    }

    /**
     * Test that we get a key event for every insert, update, remove action performed on a key. Test
     * order of events.
     *
     * @
     */
    @Test
    public void testSubscriptionKeyEvents() throws InvalidSubscriberException {
        //TODO DS connecting to a server based Java component using the clietn API can be notified by callback methods for specified key in a given map

        String testKey1 = "Key-sub-1";
        String testKey2 = "Key-sub-2";
        String testKey3 = "Key-sub-3";
        String testKey4 = "Key-sub-4";
        String testKey5 = "Key-sub-5";

        Subscriber<String> testChronicleKeyEventSubscriber = createStrictMock(Subscriber.class);

        String update1 = "Update1";
        String update2 = "Update2";
        String update3 = "Update3";
        String update4 = "Update4";
        String update5 = "Update5";

        //Set up the mock
        testChronicleKeyEventSubscriber.onMessage(testKey1);
        testChronicleKeyEventSubscriber.onMessage(testKey2);
//        testChronicleKeyEventSubscriber.onMessage(testKey3); // no event as the key doesn't exist.
        testChronicleKeyEventSubscriber.onMessage(testKey4);
        testChronicleKeyEventSubscriber.onMessage(testKey5);

        //More updates on the same keys
        testChronicleKeyEventSubscriber.onMessage(testKey1);
        testChronicleKeyEventSubscriber.onMessage(testKey2);

        //Removes
        testChronicleKeyEventSubscriber.onMessage(testKey1);
        testChronicleKeyEventSubscriber.onMessage(testKey5);

        replay(testChronicleKeyEventSubscriber);

        //Register as subscriber on map to get keys
        _clientAssetTree.registerSubscriber(_mapName, String.class, testChronicleKeyEventSubscriber);

        //Perform some puts and replace
        _stringStringMap.put(testKey1, update1);
        _stringStringMap.put(testKey2, update2);
        // this key doesn't exist so it doesn't trigger an event.
        _stringStringMap.replace(testKey3, update2, update3);
        _stringStringMap.put(testKey4, update4);
        _stringStringMap.put(testKey5, update5);

        //Perform more events on the same keys
        _stringStringMap.put(testKey1, update1);
        _stringStringMap.put(testKey2, update2);

        //Remove keys
        _stringStringMap.remove(testKey1);
        _stringStringMap.remove(testKey5);

        verify(testChronicleKeyEventSubscriber);

        // expect to be told when the tree is torn down.
        reset(testChronicleKeyEventSubscriber);
        testChronicleKeyEventSubscriber.onEndOfSubscription();
        replay(testChronicleKeyEventSubscriber);
    }

    /**
     * Test that a number of updates for a number of keys (all intermingled) all trigger events on
     * the topic in the order in which the events take place. <p> Test that removing all of the keys
     * trigger ordered events where the value is null
     *
     * @
     */
    @Test
    public void testSubscriptionOnMap() throws InvalidSubscriberException {
        //TODO DS the loops can be refactored by having a method which performs the loop and accepts a consumer
        //TODO DS connecting to a server based Java component using the client API can be notified by callback methods for all updates in map

        //Using a strict mock as we want to verify that events come in in the right order
        TopicSubscriber<String, String> topicSubscriberMock = createStrictMock(TopicSubscriber.class);
        _clientAssetTree.registerTopicSubscriber(_mapName, String.class, String.class, topicSubscriberMock);

        int noOfKeys = 5;
        int noOfValues = 5;

        //Setup the mock with the expected updates
        iterateAndExecuteConsumer((k, v) -> {
            try {
                topicSubscriberMock.onMessage(k, v);
            } catch (InvalidSubscriberException e) {
                TestCase.fail("Exception thrown");
            }
        }, c -> c % noOfKeys, c -> c, noOfKeys * noOfValues, _mapName, _mapName);

        //Setup the mock with the removes
        for (int i = 0; i < noOfKeys; i++) {
            topicSubscriberMock.onMessage(TestUtils.getKey(_mapName, i), null);
        }

        replay(topicSubscriberMock);

        //Perform the updates
        iterateAndExecuteConsumer((k, v) -> _stringStringMap.put(k, v), c -> c % noOfKeys, c -> c, noOfKeys * noOfValues, _mapName, _mapName);

        //Perform the removes
        iterateAndExecuteConsumer((k, v) -> _stringStringMap.remove(k), noOfKeys, _mapName, _mapName);

        verify(topicSubscriberMock);

        // expect to be told when the tree is torn down.
        reset(topicSubscriberMock);
        topicSubscriberMock.onEndOfSubscription();
        replay(topicSubscriberMock);

    }

    /**
     * Test event listeners on maps inserted, updated, removed are triggered correctly when expected
     * and in the correct order.
     *
     * @
     */
    @Test
    public void testMapAddedKeyListener() throws InvalidSubscriberException {
        //DS test that we can be notified when maps are added
        resetChassis();

        String parentUri = "/mapbase";
        String mapBaseUri = parentUri + "/maps";

        String mapName1 = "TestMap1";
        String mapName2 = "TestMap2";

        String mapUri1 = mapBaseUri + '/' + mapName1;
        String mapUri2 = mapBaseUri + '/' + mapName2;

        TopicSubscriber<String, String> assetTreeSubscriber = createStrictMock("assetTreeSubscriber", TopicSubscriber.class);
        registerTopicSubscriber(parentUri, String.class, String.class, assetTreeSubscriber);

        // when added
        assetTreeSubscriber.onMessage("maps", mapName1);
        assetTreeSubscriber.onMessage("maps", mapName2);

        // and when removed
        assetTreeSubscriber.onMessage("maps", mapName1);
        assetTreeSubscriber.onMessage("maps", mapName2);

        Subscriber<TopologicalEvent> mapEventKeySubscriber = createStrictMock("mapEventKeySubscriber", Subscriber.class);
        // expect a bootstrap event
        mapEventKeySubscriber.onMessage(ExistingAssetEvent.of(parentUri, "maps"));
        replay(mapEventKeySubscriber);
        registerSubscriber(mapBaseUri, TopologicalEvent.class, mapEventKeySubscriber);
        verify(mapEventKeySubscriber);
        reset(mapEventKeySubscriber);

        //First the two maps will be inserted into
        mapEventKeySubscriber.onMessage(AddedAssetEvent.of(mapBaseUri, mapName1));
        mapEventKeySubscriber.onMessage(AddedAssetEvent.of(mapBaseUri, mapName2));

        //Second the two maps are removed
        mapEventKeySubscriber.onMessage(RemovedAssetEvent.of(mapBaseUri, mapName1));
        mapEventKeySubscriber.onMessage(RemovedAssetEvent.of(mapBaseUri, mapName2));

        replay(mapEventKeySubscriber);

        //Create the two maps
        Map<String, String> map1 = acquireMap(mapUri1, String.class, String.class);
        Map<String, String> map2 = acquireMap(mapUri2, String.class, String.class);

        //Perform some actions on the maps - should not trigger events
        String keyMap1 = "KeyMap1";
        String keyMap2 = "KeyMap2";
        String valueMap1 = "ValueMap1";
        String valueMap2 = "ValueMap1";

        map1.put(keyMap1, valueMap1);
        map2.put(keyMap2, valueMap2);

        Assert.assertEquals(valueMap1, map1.get(keyMap1));
        Assert.assertNull(map1.get(keyMap2));

        Assert.assertEquals(valueMap2, map2.get(keyMap2));
        Assert.assertNull(map2.get(keyMap1));

        map1.remove(keyMap1);
        map2.remove(keyMap2);

        getAsset(mapBaseUri).removeChild(mapName1);
        getAsset(mapBaseUri).removeChild(mapName2);

        verify(mapEventKeySubscriber);
        reset(mapEventKeySubscriber);
        mapEventKeySubscriber.onEndOfSubscription();
        replay(mapEventKeySubscriber);
    }

    /**
     * Perform a for loop for the noOfKeys (from 0) and perform the methodToExecute with the given
     * key (manipulated) and given value (manipulated).
     *
     * @param methodToExecute   Method to be executed for each iteration.
     * @param keyManipulation   Manipulation to be performed on the key counter value before before
     *                          creating the key.
     * @param valueManipulation Manipulation to be performed on the value counter value before
     *                          before creating the value.
     * @param noOfKeys          No of iterations.
     * @param keyBase           Base value for the key - typically the map name.
     * @param valueBase         Base value for the value - typically the map name.
     */
    private void iterateAndExecuteConsumer(BiConsumer<String, String> methodToExecute, Function<Integer, Integer> keyManipulation, Function<Integer, Integer> valueManipulation, int noOfKeys, String keyBase, String valueBase) {
        IntStream.range(0, noOfKeys).forEach((i) -> methodToExecute.accept(TestUtils.getKey(keyBase, keyManipulation.apply(i)), TestUtils.getValue(valueBase, valueManipulation.apply(i))));
    }

    /**
     * Perform a for loop for the noOfKeys (from 0) and perform the methodToExecute with key based
     * on base value and counter and a value based on the base value and the counter.
     *
     * @param methodToExecute Method to be executed for each iteration.
     * @param noOfKeys        No of iterations.
     * @param keyBase         Base value for the key - typically the map name.
     * @param valueBase       Base value for the value - typically the map name.
     */
    private void iterateAndExecuteConsumer(BiConsumer<String, String> methodToExecute, int noOfKeys, String keyBase, String valueBase) {
        iterateAndExecuteConsumer(methodToExecute, c -> c, c -> c, noOfKeys, keyBase, valueBase);
    }

    //TODO DS how do we remove maps? Add test. - see testMapAddedKeyListener()
}