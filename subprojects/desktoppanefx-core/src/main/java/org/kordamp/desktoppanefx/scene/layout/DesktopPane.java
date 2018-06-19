/*
 * Copyright 2015-2018 Andres Almiray
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kordamp.desktoppanefx.scene.layout;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Lincoln Minto
 * @author Andres Almiray
 */
public class DesktopPane extends VBox {
    private final AnchorPane internalWindowContainer;
    private final ScrollPane taskBar;
    private HBox tbWindows;
    private DesktopPane desktopPane = this;
    private final int TASKBAR_HEIGHT_WITHOUT_SCROLL = 44;
    private final int TASKBAR_HEIGHT_WITH_SCROLL = TASKBAR_HEIGHT_WITHOUT_SCROLL + 10;

    /**
     * *********** CONSTRUICTOR *************
     */
    public DesktopPane() {
        super();
        getStylesheets().add("/org/kordamp/desktoppanefx/scene/layout/default-desktoppane-stylesheet.css");
        setAlignment(Pos.TOP_LEFT);

        internalWindowContainer = new AnchorPane();
        internalWindowContainer.getStyleClass().add("desktoppane");
        tbWindows = new HBox();
        tbWindows.setSpacing(3);
        tbWindows.setMaxHeight(TASKBAR_HEIGHT_WITHOUT_SCROLL);
        tbWindows.setMinHeight(TASKBAR_HEIGHT_WITHOUT_SCROLL);
        tbWindows.setAlignment(Pos.CENTER_LEFT);
        setVgrow(internalWindowContainer, Priority.ALWAYS);
        taskBar = new ScrollPane(tbWindows);
        Platform.runLater(() -> {
            Node viewport = taskBar.lookup(".viewport");
            try {
                viewport.setStyle(" -fx-background-color: transparent; ");
            } catch (Exception e) {
            }
        });
        taskBar.setMaxHeight(TASKBAR_HEIGHT_WITHOUT_SCROLL);
        taskBar.setMinHeight(TASKBAR_HEIGHT_WITHOUT_SCROLL);
        taskBar.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        taskBar.setVmax(0);
        taskBar.getStyleClass().add("desktoppane-taskbar");

        tbWindows.widthProperty().addListener((o, v, n) -> {
            Platform.runLater(() -> {
                if (n.doubleValue() <= taskBar.getWidth()) {
                    taskBar.setMaxHeight(TASKBAR_HEIGHT_WITHOUT_SCROLL);
                    taskBar.setPrefHeight(TASKBAR_HEIGHT_WITHOUT_SCROLL);
                    taskBar.setMinHeight(TASKBAR_HEIGHT_WITHOUT_SCROLL);
                } else {
                    taskBar.setMaxHeight(TASKBAR_HEIGHT_WITH_SCROLL);
                    taskBar.setPrefHeight(TASKBAR_HEIGHT_WITH_SCROLL);
                    taskBar.setMinHeight(TASKBAR_HEIGHT_WITH_SCROLL);
                }
            });
        });
        getChildren().addAll(internalWindowContainer, taskBar);

        addEventHandler(MDIEvent.EVENT_CLOSED, mdiCloseHandler);
        addEventHandler(MDIEvent.EVENT_MINIMIZED, mdiMinimizedHandler);
    }

    /**
     * ***********************GETTER***********************************
     */
    public Pane getInternalWindowContainer() {
        return internalWindowContainer;
    }

    public Pane getTbWindows() {
        return tbWindows;
    }

    /**
     * *************************REMOVE_WINDOW******************************
     */
    public void removeMDIWindow(String mdiWindowID) {
        Node mdi = getItemFromMDIContainer(mdiWindowID);
        Node iconBar = getItemFromToolBar(mdiWindowID);

        if (mdi != null) {
            getItemFromMDIContainer(mdiWindowID).isClosed(true);

            internalWindowContainer.getChildren().remove(mdi);
        }
        if (iconBar != null) {
            tbWindows.getChildren().remove(iconBar);
        }
    }

    /**
     * *****************************ADD_WINDOW*********************************
     */
    public void addMDIWindow(InternalWindow internalWindow) {
        if (getItemFromMDIContainer(internalWindow.getId()) == null) {
            addNew(internalWindow, null);
        } else {
            restoreExisting(internalWindow);
        }
    }

    public void addMDIWindow(InternalWindow internalWindow, Point2D position) {
        if (getItemFromMDIContainer(internalWindow.getId()) == null) {
            addNew(internalWindow, position);
        } else {
            restoreExisting(internalWindow);
        }
    }

    private void addNew(InternalWindow internalWindow, Point2D position) {
        internalWindow.setVisible(false);
        internalWindowContainer.getChildren().add(internalWindow);
        if (position == null) {
            internalWindow.layoutBoundsProperty().addListener(new WidthChangeListener(this, internalWindow));
        } else {
            this.placeMdiWindow(internalWindow, position);
        }
        internalWindow.toFront();
    }

    private void restoreExisting(InternalWindow internalWindow) {
        if (getItemFromToolBar(internalWindow.getId()) != null) {
            tbWindows.getChildren().remove(getItemFromToolBar(internalWindow.getId()));
        }
        for (int i = 0; i < internalWindowContainer.getChildren().size(); i++) {
            Node node = internalWindowContainer.getChildren().get(i);
            if (node.getId().equals(internalWindow.getId())) {
                node.toFront();
                node.setVisible(true);
            }
        }
    }

    /**
     * *****************************MDI_EVENT_HANDLERS**************************
     */
    public EventHandler<MDIEvent> mdiCloseHandler = new EventHandler<MDIEvent>() {
        @Override
        public void handle(MDIEvent event) {
            InternalWindow win = (InternalWindow) event.getTarget();
            tbWindows.getChildren().remove(getItemFromToolBar(win.getId()));
            win = null;
        }
    };
    public EventHandler<MDIEvent> mdiMinimizedHandler = new EventHandler<MDIEvent>() {
        @Override
        public void handle(MDIEvent event) {
            InternalWindow win = (InternalWindow) event.getTarget();
            String id = win.getId();
            if (getItemFromToolBar(id) == null) {
                try {
                    TaskBarIcon icon = new TaskBarIcon(event.imgLogo, desktopPane, win.getWindowsTitle());
                    icon.setId(win.getId());
                    icon.getBtnClose().disableProperty().bind(win.getBtnClose().disableProperty());
                    tbWindows.getChildren().add(icon);
                } catch (Exception ex) {
                    Logger.getLogger(DesktopPane.class.getName()).log(Level.SEVERE, null, ex);
                }

            }
        }
    };

    /**
     * ***************** UTILITIES******************************************
     */
    public TaskBarIcon getItemFromToolBar(String id) {
        for (Node node : tbWindows.getChildren()) {
            if (node instanceof TaskBarIcon) {
                TaskBarIcon icon = (TaskBarIcon) node;
                //String key = icon.getLblName().getText();
                String key = icon.getId();
                if (key.equalsIgnoreCase(id)) {
                    return icon;
                }
            }
        }
        return null;
    }

    public InternalWindow getItemFromMDIContainer(String id) {
        for (Node node : internalWindowContainer.getChildren()) {
            if (node instanceof InternalWindow) {
                InternalWindow win = (InternalWindow) node;
                if (win.getId().equals(id)) {

                    return win;
                }
            }
        }
        return null;
    }

    public void placeMdiWindow(InternalWindow internalWindow, InternalWindow.AlignPosition alignPosition) {
        double canvasH = getInternalWindowContainer().getLayoutBounds().getHeight();
        double canvasW = getInternalWindowContainer().getLayoutBounds().getWidth();
        double mdiH = internalWindow.getLayoutBounds().getHeight();
        double mdiW = internalWindow.getLayoutBounds().getWidth();

        switch (alignPosition) {
            case CENTER:
                centerMdiWindow(internalWindow);
                break;
            case CENTER_LEFT:
                placeMdiWindow(internalWindow, new Point2D(0, (int) (canvasH / 2) - (int) (mdiH / 2)));
                break;
            case CENTER_RIGHT:
                placeMdiWindow(internalWindow, new Point2D((int) canvasW - (int) mdiW, (int) (canvasH / 2) - (int) (mdiH / 2)));
                break;
            case TOP_CENTER:
                placeMdiWindow(internalWindow, new Point2D((int) (canvasW / 2) - (int) (mdiW / 2), 0));
                break;
            case TOP_LEFT:
                placeMdiWindow(internalWindow, Point2D.ZERO);
                break;
            case TOP_RIGHT:
                placeMdiWindow(internalWindow, new Point2D((int) canvasW - (int) mdiW, 0));
                break;
            case BOTTOM_LEFT:
                placeMdiWindow(internalWindow, new Point2D(0, (int) canvasH - (int) mdiH));
                break;
            case BOTTOM_RIGHT:
                placeMdiWindow(internalWindow, new Point2D((int) canvasW - (int) mdiW, (int) canvasH - (int) mdiH));
                break;
            case BOTTOM_CENTER:
                placeMdiWindow(internalWindow, new Point2D((int) (canvasW / 2) - (int) (mdiW / 2), (int) canvasH - (int) mdiH));
                break;
        }
    }

    public void placeMdiWindow(InternalWindow internalWindow, Point2D point) {
        double windowsWidth = internalWindow.getLayoutBounds().getWidth();
        double windowsHeight = internalWindow.getLayoutBounds().getHeight();
        internalWindow.setPrefSize(windowsWidth, windowsHeight);

        double containerWidth = this.internalWindowContainer.getLayoutBounds().getWidth();
        double containerHeight = this.internalWindowContainer.getLayoutBounds().getHeight();
        if (containerWidth <= point.getX() || containerHeight <= point.getY()) {
            throw new PositionOutOfBoundsException(
                "Tried to place MDI Window with ID " + internalWindow.getId() +
                    " at a coordinate " + point.toString() +
                    " that is beyond current size of the MDI container " +
                    containerWidth + "px x " + containerHeight + "px."
            );
        }

        if ((containerWidth - point.getX() < 40) ||
            (containerHeight - point.getY() < 40)) {
            throw new PositionOutOfBoundsException(
                "Tried to place MDI Window with ID " + internalWindow.getId() +
                    " at a coordinate " + point.toString() +
                    " that is too close to the edge of the parent of size " +
                    containerWidth + "px x " + containerHeight + "px " +
                    " for user to comfortably grab the title bar with the mouse."
            );
        }

        internalWindow.setLayoutX((int) point.getX());
        internalWindow.setLayoutY((int) point.getY());
        internalWindow.setVisible(true);
    }

    public void centerMdiWindow(InternalWindow internalWindow) {
        double w = getInternalWindowContainer().getLayoutBounds().getWidth();
        double h = getInternalWindowContainer().getLayoutBounds().getHeight();

        Platform.runLater(() -> {
            double windowsWidth = internalWindow.getLayoutBounds().getWidth();
            double windowsHeight = internalWindow.getLayoutBounds().getHeight();

            Point2D centerCoordinate = new Point2D(
                (int) (w / 2) - (int) (windowsWidth / 2),
                (int) (h / 2) - (int) (windowsHeight / 2)
            );
            this.placeMdiWindow(internalWindow, centerCoordinate);
        });
    }

    public static InternalWindow resolveInternalWindow(Node node) {
        if (node == null) {
            return null;
        }

        Node candidate = node;
        while (candidate != null) {
            if (candidate instanceof InternalWindow) {
                return (InternalWindow) candidate;
            }
            candidate = candidate.getParent();
        }

        return null;
    }

    private static class WidthChangeListener implements ChangeListener {
        private DesktopPane desktopPane;
        private InternalWindow window;

        public WidthChangeListener(DesktopPane desktopPane, InternalWindow window) {
            this.desktopPane = desktopPane;
            this.window = window;
        }

        @Override
        public void changed(ObservableValue observable, Object oldValue, Object newValue) {
            this.desktopPane.centerMdiWindow(this.window);
            observable.removeListener(this);
        }
    }
}
