package jo.vpl.core;

import java.beans.PropertyChangeEvent;
import javafx.scene.layout.VBox;
import java.util.*;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.Tooltip;
import javafx.scene.input.*;
import jo.vpl.util.TypeExtensions;

/**
 *
 * @author JoostMeulenkamp
 */
public class Port extends VBox {

    private final ObjectProperty data = new SimpleObjectProperty(this, "data", null);
    private final BooleanProperty active = new SimpleBooleanProperty(this, "active", false);
    private final StringProperty name = new SimpleStringProperty(this, "name", null);

    public ObservableList<Connection> connectedConnections;
    public Class dataType;
    public PortTypes portType;
    public Hub parentHub;
    public boolean multiDockAllowed;
    public BindingPoint origin;
    public int index;

    public Port(String name, Hub parent, PortTypes portType, Class type) {
        Tooltip tip = new Tooltip();
        Tooltip.install(this, tip);
        tip.textProperty().bind(this.nameProperty());

        this.parentHub = parent;
        this.dataType = type;
        this.portType = portType;
        this.setName(name);
        
        if(portType == PortTypes.IN){
            index = parent.inPorts.size();
        } else {
            index = parent.outPorts.size();
        }

        getStyleClass().add("port");
        getStyleClass().add("port-" + portType.toString().toLowerCase());

        connectedConnections = FXCollections.observableArrayList();
        connectedConnections.addListener(this::handle_ConnectionChange);

        origin = new BindingPoint(0, 0);

        setOnMousePressed(this::port_MousePress);
        setOnMouseDragged(this::port_MouseDrag);

        boundsInParentProperty().addListener(this::handle_SizeChange);
        parentHub.eventBlaster.add(this::parentHub_PropertyChanged);

        active.addListener(this::handle_Active);

    }

    private void handle_ConnectionChange(ListChangeListener.Change change) {
        if (connectedConnections.size() == 0) {
            setActive(false);
        } else {
            setActive(true);
        }
    }

    private void calcOrigin() {
        origin.x().setValue(parentHub.hostCanvas.sceneToLocal(localToScene(getWidth() / 2, getHeight() / 2)).getX());
        origin.y().setValue(parentHub.hostCanvas.sceneToLocal(localToScene(getWidth() / 2, getHeight() / 2)).getY());

    }

    private void handle_SizeChange(Object obj, Object oldVal, Object newVal) {
        calcOrigin();
    }

    private void parentHub_PropertyChanged(PropertyChangeEvent e) {
        calcOrigin();
    }

    /**
     * @TODO CHANGE FROM ORIGINAL CODE Consume event to prevent hub from moving
     * around.
     *
     * @param e
     */
    private void port_MouseDrag(MouseEvent e) {
        e.consume();
    }

    private void port_MousePress(MouseEvent e) {
        /**
         * @TODO CHANGE FROM ORIGINAL CODE Origin is only calculated on size-
         * and property changed
         */
        calcOrigin();

        switch (parentHub.hostCanvas.splineMode) {
            case NOTHING:
                parentHub.hostCanvas.tempStartPort = this;
                parentHub.hostCanvas.splineMode = SplineMode.SECOND;
                break;

            case SECOND:
                /**
                 * Check if the data type from the sending port is the same or a
                 * sub class of the receiving port.
                 */
                if (((TypeExtensions.isCastableTo(parentHub.hostCanvas.tempStartPort.dataType, dataType)
                        && parentHub.hostCanvas.typeSensitive && portType == PortTypes.IN)
                        || (TypeExtensions.isCastableTo(dataType, parentHub.hostCanvas.tempStartPort.dataType)
                        && parentHub.hostCanvas.typeSensitive && portType == PortTypes.OUT)
                        // IN case dataProperty type does not matter
                        || (!parentHub.hostCanvas.typeSensitive))
                        // Cannot be the same port type; IN > OUT or OUT > IN
                        && portType != parentHub.hostCanvas.tempStartPort.portType
                        // Cannot be the same hub
                        && !parentHub.equals(parentHub.hostCanvas.tempStartPort.parentHub)) {

                    Connection connection;

                    /**
                     * Make a new connection and remove all the existing
                     * connections Where is multi connect?
                     */
                    if (portType == PortTypes.OUT) {
                        if (parentHub.hostCanvas.tempStartPort.connectedConnections.size() > 0) {

                            if (!parentHub.hostCanvas.tempStartPort.multiDockAllowed) {
                                for (Connection c : parentHub.hostCanvas.tempStartPort.connectedConnections) {
                                    c.removeFromCanvas();
                                }
                            }
                        }
                        connection = new Connection(parentHub.hostCanvas, this, parentHub.hostCanvas.tempStartPort);

                    } else {
                        if (connectedConnections.size() > 0) {

                            if (!multiDockAllowed) {
                                for (Connection c : connectedConnections) {
                                    c.removeFromCanvas();
                                    c.startPort.connectedConnections.remove(c);
                                }
                                connectedConnections.clear();
                            }
                        }
                        connection = new Connection(parentHub.hostCanvas, parentHub.hostCanvas.tempStartPort, this);
                    }
                    parentHub.hostCanvas.connectionSet.add(connection);

                }
                /**
                 * Return values back to default state in which no connection is
                 * being made.
                 */
                parentHub.hostCanvas.splineMode = SplineMode.NOTHING;
                parentHub.hostCanvas.clearTempLine();
                break;

        }
        e.consume();
    }

    public final ObjectProperty dataProperty() {
        return data;
    }

    public final void setData(Object value) {
        calculateData(value);
    }

    public final Object getData() {
        return data.get();
    }

    //Double point operators do NOT work when trying to remove listeners
    //USE THIS OTHERWISE THERE WILL BE MEMORY LEAKING
    ChangeListener startPort_DataChangeListener = new ChangeListener() {

        @Override
        public void changed(ObservableValue obj, Object oldVal, Object newVal) {
            calculateData(newVal);
        }
    };

//    OBSOLETE CODE, SINCE dataChanged IS ALREADY THIS AND IS MONITORED?
//    public void OnDataChanged() {
//        if (DataChanged != null) {
//            DataChanged(this, new EventArgs());
//        }
//    }
    public void calculateData() {
        calculateData(null);
    }

    public void calculateData(Object value) {

        boolean fxThread = Thread.currentThread().getName().equals("JavaFX Application Thread");
        if (!fxThread) {
            System.out.println(this.parentHub.getName());
        }

        if (portType == PortTypes.IN) {

            if (multiDockAllowed && connectedConnections.size() > 1) {

                dataType.cast(new Object());
                List listOfLists = new ArrayList<>();

//                var listType = typeof(List < >).MakeGenericType(new Type[]{DataType});
//                IList list = (IList) Activator.CreateInstance(listType);
                for (Connection connection : connectedConnections) {

                    //Cast all primitive dataType to String if this port dataType is String
                    Port startPort = connection.getStartPort();
                    if (dataType == String.class && TypeExtensions.contains(startPort.dataType)) {
                        if (startPort.getData() instanceof List) {
                            List list = (List) startPort.getData();
                            List newList = new ArrayList<>();
                            for (Object primitive : list) {
                                newList.add(primitive + "");
                            }
                            listOfLists.add(newList);
                        } else {
                            listOfLists.add(startPort.getData() + "");
                        }
                    } else {
                        listOfLists.add(startPort.getData());
                    }

                }
                data.set(listOfLists);

            } else if (connectedConnections.size() > 0) {
                System.out.println("Data Received: " + value);

                //Cast all primitive dataType to String if this port dataType is String
                Port startPort = connectedConnections.get(0).getStartPort();
                if (dataType == String.class && TypeExtensions.contains(startPort.dataType)) {
                    if (startPort.getData() instanceof List) {
                        List list = (List) startPort.getData();
                        List newList = new ArrayList<>();
                        for (Object primitive : list) {
                            newList.add(primitive + "");
                        }
                        data.set(newList);
                    } else {
                        data.set(startPort.getData() + "");
                    }
                } else {
                    data.set(startPort.getData());
                }

            } else {
                data.set(null);
            }
        } else {
            data.set(value);
        }
        //OnDataChanged();
    }

    public final void setActive(boolean value) {
        active.set(value);
    }

    public final boolean isActive() {
        return active.get();
    }

    public BooleanProperty activeProperty() {
        return active;
    }

    public final void setName(String value) {
        name.set(value);
    }

    public final String getName() {
        return name.get();
    }

    public StringProperty nameProperty() {
        return name;
    }

    private void handle_Active(Object obj, Object oldVal, Object newVal) {
        if (isActive()) {
            getStyleClass().remove("port");
            getStyleClass().add("port-active");
        } else {
            getStyleClass().remove("port-active");
            getStyleClass().add("port");
        }
    }
}
