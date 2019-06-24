package application;

import javafx.application.*;
import javafx.event.*;
import javafx.geometry.*;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.text.*;
import javafx.scene.layout.*;

import java.io.*;
import java.net.*;
import java.util.Optional;
import java.util.Random;

public class Main extends Application {

	private GridPane playboard = null;
	private Player user = null;
	private Player opponent = null;
	private String pName = "";
	private String oName = "";
	private ReversiBoard rboard = null;
	private int pColor = 0; //player color
	private Computer comp = null;
	private int oColor = 0; //opponent color
	private int tColor = 0; //color of entity taking this turn

	private Socket socket = null;
	private DataInputStream in = null;
	private DataOutputStream out = null;

	private Scene mainscene = null;
	private Stage pStage = null;
	private Text winAnnounce = new Text();

	private boolean listening; //for the master listener thread

	@Override
	public void start(Stage primaryStage) {
		try {
			//creates region in the main page
			VBox mainroot = setMainScreen(primaryStage);

			//create scene with root
			mainscene = new Scene(mainroot,900,600);
			mainscene.getStylesheets().add(getClass().getResource("application.css").toExternalForm());

			//puts scene on stage
			primaryStage.setScene(mainscene);
			primaryStage.setTitle("Reversi Launcher 3000");

			pStage = primaryStage;

			//display
			primaryStage.show();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	//creates the Region for the main screen
	public VBox setMainScreen(Stage primaryStage) {

		//creates the actual vbox
		VBox main = new VBox();
		main.setSpacing(30);
		main.setId("mainvbox");

		//adds title
		Text title = new Text("REVERSI");
		title.setId("titletext");
		main.getChildren().add(title);

		//adds buttons
		Button singlePlayerButton = new Button("Singleplayer");
		singlePlayerButton.getStyleClass().add("mainbutton");
		Button multiPlayerButton = new Button("Multiplayer");
		multiPlayerButton.getStyleClass().add("mainbutton");
		Button quitButton = new Button("Quit Game");
		quitButton.getStyleClass().add("mainbutton");
		main.getChildren().add(singlePlayerButton);
		main.getChildren().add(multiPlayerButton);
		main.getChildren().add(quitButton);


		//when single player is clicked
		EventHandler<ActionEvent> SPClicked = new EventHandler<ActionEvent>() { 
			public void handle(ActionEvent e) 
			{ 
				rboard = new ReversiBoard();

				TextInputDialog dialog = new TextInputDialog("");
				dialog.setTitle("Name");
				dialog.setHeaderText("Name input");
				dialog.setContentText("Please enter your name:");
				Optional<String> result = dialog.showAndWait();
				String name = "";
				if (result.isPresent()){
					name = result.get();
					if (name.trim().length() == 0)
						return;
				}
				else {
					return;
				}

				Random rand = new Random();
				pColor = rand.nextInt(2) * 2 - 1;
				user = new Player(name, rboard, pColor);
				comp = new Computer(rboard, pColor * -1);
				oColor = pColor * -1;

				BorderPane gameRegion = createGameRegion();
				Scene gameScene = new Scene(gameRegion, 900, 600);
				gameScene.getStylesheets().add(getClass().getResource("application.css").toExternalForm());
				primaryStage.setScene(gameScene);
			} 
		}; 

		//when multi player is clicked
		EventHandler<ActionEvent> MPClicked = new EventHandler<ActionEvent>() { 
			public void handle(ActionEvent e) 
			{ 

				rboard = new ReversiBoard();

				Random rand = new Random();
				TextInputDialog dialog = new TextInputDialog("Player" + rand.nextInt(100));
				dialog.setTitle("Name");
				dialog.setHeaderText("Name input");
				dialog.setContentText("Please enter your name:");
				Optional<String> result = dialog.showAndWait();
				String name = "";
				if (result.isPresent()) {
					name = result.get();
					if (name.trim().length() == 0)
						return;
				}
				else {
					return;
				}
				pName = name;

				String ip;
				TextInputDialog ipdialog = new TextInputDialog();
				ipdialog.setTitle("Connection");
				ipdialog.setHeaderText("Connect to Server");
				ipdialog.setContentText("Enter the server's ip address.");
				Optional<String> result2 = ipdialog.showAndWait();
				if (result2.isPresent()) {
					ip = result2.get();
					if (ip.trim().length() == 0)
						return;
				}
				else {
					return;
				}

				Platform.runLater(new Runnable()
				{
					@Override
					public void run() {
						try {
							boolean connected = connect(ip);
							if (connected) {
								BorderPane gameRegion = createMultiplayerGameRegion();
								Scene gameScene = new Scene(gameRegion, 900, 600);
								gameScene.getStylesheets().add(getClass().getResource("application.css").toExternalForm());
								primaryStage.setScene(gameScene);
							}
						} catch (ClassNotFoundException | IOException e) {
							e.printStackTrace();
						}
					}
				});

			} 
		};

		//when quit is clicked
		EventHandler<ActionEvent> QuitClicked = new EventHandler<ActionEvent>() {
			public void handle(ActionEvent e)
			{
				Platform.exit();
			}
		};

		singlePlayerButton.setOnAction(SPClicked);
		multiPlayerButton.setOnAction(MPClicked);
		quitButton.setOnAction(QuitClicked);

		return main;

	}

	//attempts to connect to the server with ip
	public boolean connect(String ip) throws IOException, ClassNotFoundException {

		socket = new Socket(ip, 5337);

		out = new DataOutputStream(socket.getOutputStream());
		in = new DataInputStream(socket.getInputStream());

		out.writeUTF("USERNAME#" + pName);
		out.flush();

		return true;

	}

	//creates the borderpane for the multiplayer game
	public BorderPane createMultiplayerGameRegion() {

		tColor = -1;

		BorderPane region = new BorderPane();
		region.setId("playregion");

		//create top text
		Text heading = new Text("REVERSI");
		heading.setId("playregionhead");

		//create connect text
		Text connectText = new Text();

		//MASTER LISTENER THREAD
		//listens to all possible messages
		class Listener extends Thread {
			
			public void run() {
				try {
					while (listening) {
						String[] received = in.readUTF().split("#");
						String keyword = received[0];
						if (keyword.equals("MOVE")) {
							int oppoX = Integer.parseInt(received[1]);
							int oppoY = Integer.parseInt(received[2]);
							int[][] oldboard = rboard.getBoard();
							opponent.makeMove(oppoX, oppoY);
							tColor = pColor;
							boolean canUserPlay = updateBoard(oldboard);
							if (!canUserPlay) {
								out.writeUTF("PASS");
							}
						}
						else if (keyword.equals("PASS")) { //opponent decided to pass
							tColor = pColor;
							int[][] oldboard = rboard.getBoard();
							boolean canUserPlay = updateBoard(oldboard);
							if (!canUserPlay) {
								out.writeUTF("GAMEEND");
								listening = false;
								endGame();
							}
						}
						else if (keyword.equals("FOUNDOPPONENT")) {
							//everything here is deciding who goes first
							oName = received[1];
							Random rand = new Random();

							//whoever has the larger number goes first
							double num = rand.nextDouble();
							out.writeUTF(num + "");
							double opponum = Double.parseDouble(in.readUTF());
							if (num > opponum) {
								pColor = -1;
								oColor = 1;
								connectText.setText("Opponent found! You are black.");
								int[][] oldboard = rboard.getBoard();
								updateBoard(oldboard);
							}
							else {
								pColor = 1;
								oColor = -1;
								connectText.setText("Opponent found! You are white.");
								int[][] oldboard = rboard.getBoard();
								updateBoard(oldboard);
								disableAllButtons();
							}
							user = new Player(pName, rboard, pColor);
							opponent = new Player(oName, rboard, oColor);
						}
						else if (keyword.equals("OPPOEXIT")) {
							out.writeUTF("OPPOEXIT");
							connectText.setText("Opponent disconnected.");
							listening = false;
						}
						else if (keyword.equals("GAMEEND")) {
							endGame();
							listening = false;
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

		}
		listening = true;
		Listener listener = new Listener();
		listener.start();

		//create Connect button
		Button connectButton = new Button("Find opponent");
		EventHandler<ActionEvent> connectClicked = new EventHandler<ActionEvent>() {
			public void handle(ActionEvent e) {
				try {
					connectText.setText("Looking for opponent...");
					out.writeUTF("FINDOPPONENT");
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				connectButton.setDisable(true);
			}
		};
		connectButton.setOnAction(connectClicked);

		//create the board region
		playboard = new GridPane();
		playboard.setId("playboard");

		//sets sizes of row and column
		for (int i = 0; i < 8; i++) {
			int size = 50;
			RowConstraints rc = new RowConstraints(size);
			playboard.getRowConstraints().add(rc);
			ColumnConstraints cc = new ColumnConstraints(size);
			playboard.getColumnConstraints().add(cc);
		}

		//adds each button
		for (int x = 0; x < 8; x++) {
			for (int y = 0; y < 8; y++) {
				GridButton button = new GridButton(x, y);
				button.getStyleClass().add("gridsquare");
				button.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

				EventHandler<ActionEvent> boardClicked = new EventHandler<ActionEvent>() {
					public void handle(ActionEvent e)
					{

						int[][] oldboard = rboard.getBoard();

						int[] selCoords = button.getcoord();
						user.makeMove(selCoords[0], selCoords[1]);
						try {
							out.writeUTF("MOVE#" + selCoords[0] + "#" + selCoords[1]);
						} catch (IOException e1) {
							e1.printStackTrace();
						}

						tColor = oColor;
						updateBoard(oldboard);
						disableAllButtons();
					}
				};

				button.setOnAction(boardClicked);
				button.setDisable(true);
				playboard.add(button, x, y);
			}
		}

		//create back button
		Button backButton = new Button("Exit");
		EventHandler<ActionEvent> exitClicked = new EventHandler<ActionEvent>() {
			public void handle(ActionEvent event) {
				try {
					Alert confirmation = new Alert(AlertType.CONFIRMATION);
					confirmation.setContentText("Are you sure you want to exit the game?");
					ButtonType yes = new ButtonType("Yes");
					ButtonType no = new ButtonType("No");
					confirmation.getButtonTypes().setAll(yes, no);
					Optional<ButtonType> result = confirmation.showAndWait();
					if (result.get() == yes) {
						out.writeUTF("EXIT");
						listening = false;
						in.close();
						out.close();
						socket.close();
						pStage.setScene(mainscene);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		};
		backButton.setOnAction(exitClicked);

		//create top hbox
		HBox topHBox = new HBox();
		topHBox.getChildren().add(backButton);
		topHBox.getChildren().add(heading);
		topHBox.getChildren().add(winAnnounce);

		//create right vbox
		VBox connectVbox = new VBox();
		connectVbox.getChildren().add(connectButton);
		connectVbox.getChildren().add(connectText);

		region.setTop(topHBox);
		region.setCenter(playboard);
		region.setRight(connectVbox);
		return region;
	}

	//disables all buttons, called when waiting for opponent's turn
	public void disableAllButtons() {

		for (int x = 0; x < 8; x++) {
			for (int y = 0; y < 8; y++) {
				GridButton button = (GridButton)playboard.getChildren().get(8 * x + y);
				button.setDisable(true);
			}
		}

	}

	//creates the borderpane for the single player game
	public BorderPane createGameRegion() {

		tColor = -1;

		BorderPane region = new BorderPane();
		region.setId("playregion");

		//create top text
		Text heading = new Text("REVERSI");
		heading.setId("playregionhead");

		//create back button
		Button backButton = new Button("Exit");
		EventHandler<ActionEvent> exitClicked = new EventHandler<ActionEvent>() {
			public void handle(ActionEvent event) {
				Alert confirmation = new Alert(AlertType.CONFIRMATION);
				confirmation.setContentText("Are you sure you want to exit the game?");
				ButtonType yes = new ButtonType("Yes");
				ButtonType no = new ButtonType("No");
				confirmation.getButtonTypes().setAll(yes, no);
				Optional<ButtonType> result = confirmation.showAndWait();
				if (result.get() == yes)
					pStage.setScene(mainscene);
			}
		};
		backButton.setOnAction(exitClicked);

		//create the board region
		playboard = new GridPane();
		playboard.setId("playboard");

		//sets sizes of row and column
		for (int i = 0; i < 8; i++) {
			int size = 50;
			RowConstraints rc = new RowConstraints(size);
			playboard.getRowConstraints().add(rc);
			ColumnConstraints cc = new ColumnConstraints(size);
			playboard.getColumnConstraints().add(cc);
		}

		//adds each button
		for (int x = 0; x < 8; x++) {
			for (int y = 0; y < 8; y++) {
				GridButton button = new GridButton(x, y);
				button.getStyleClass().add("gridsquare");
				//Image img = new Image(getClass().getResourceAsStream("emptysquare.png"));
				//button.setGraphic(new ImageView(img));
				button.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

				EventHandler<ActionEvent> boardClicked = new EventHandler<ActionEvent>() {
					public void handle(ActionEvent e)
					{

						int[][] oldboard = rboard.getBoard();
						boolean canComputerPlay;

						int[] selCoords = button.getcoord();
						user.makeMove(selCoords[0], selCoords[1]);

						tColor = oColor;
						canComputerPlay = updateBoard(oldboard);

						if (canComputerPlay) {
							boolean canUserPlay;
							while (true) {
								oldboard = rboard.getBoard();
								comp.makeMove();
								tColor = pColor;
								canUserPlay = updateBoard(oldboard);
								if (canUserPlay)
									break;
								oldboard = rboard.getBoard();
								tColor = oColor;
								canComputerPlay = updateBoard(oldboard);
								if (!canComputerPlay) {
									endGame();
									break;
								}
							}
						}
						else {
							tColor = pColor;
							oldboard = rboard.getBoard();
							boolean canUserPlay = updateBoard(oldboard);
							if (!canUserPlay)
								endGame();
						}
					}
				};

				button.setOnAction(boardClicked);
				playboard.add(button, x, y);
			}
		}

		int[][] oldboard = rboard.getBoard();
		updateBoard(oldboard);
		if (user.getColor() == 1) {
			comp.makeMove();
			tColor *= -1;
			updateBoard(oldboard);
		}

		//create top hbox
		HBox topHBox = new HBox();
		topHBox.getChildren().add(backButton);
		topHBox.getChildren().add(heading);
		topHBox.getChildren().add(winAnnounce);

		region.setTop(topHBox);
		region.setCenter(playboard);
		return region;
	}

	//updates which buttons are disabled through legalboard, and which colors are which through the actual board
	public boolean updateBoard(int[][] oldboard) {

		boolean canPlay = !rboard.updateLegal(tColor);
		int[][] newboard = rboard.getBoard();
		int[][][] legalboard = rboard.getLegal();

		for (int x = 0; x < 8; x++) {
			for (int y = 0; y < 8; y++) {
				GridButton button = (GridButton)playboard.getChildren().get(8 * x + y);
				if (rboard.getSpace(x, y) == 1) {
					button.getStyleClass().clear();
					button.setStyle("null");
					if (newboard[x][y] != oldboard[x][y]) {
						if (pColor == 1)
							button.getStyleClass().add("wsrecentself");
						else
							button.getStyleClass().add("wsrecentoppo");
					}
					else {
						button.getStyleClass().add("whitesquare");
					}
				}
				else if (rboard.getSpace(x, y) == -1) {
					button.getStyleClass().clear();
					button.setStyle("null");
					if (newboard[x][y] != oldboard[x][y]) {
						if (pColor == -1)
							button.getStyleClass().add("bsrecentself");
						else
							button.getStyleClass().add("bsrecentoppo");
					}
					else {
						button.getStyleClass().add("blacksquare");
					}
				}

				if (legalboard[x][y][0] == 0)
					button.setDisable(true);
				else
					button.setDisable(false);
			}
		}

		return canPlay;

	}

	public void endGame() {
		
		int wColor = rboard.findWinner();
		if (wColor == pColor) {
			winAnnounce.setText("You win!");
		}
		else if (wColor == pColor * -1) {
			winAnnounce.setText("You lose!");
		}
		else {
			winAnnounce.setText("You tied with your opponent!");
		}
		//Platform.exit();

	}

	public void init() {
		System.out.println("Initiating Reversi Launcher 3000...");
	}

	public void stop() throws IOException {
		if (socket != null) {
			out.writeUTF("EXIT");
			in.close();
			out.close();
			socket.close();
		}
		System.out.println("Shutting down.");
	}

	public static void main(String[] args) {
		launch(args);
	}
}