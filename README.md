# CPD Projects

CPD Projects of group T11G12.

Group members:

1. Diogo Ramos (up202207954@up.pt)
2. Gonçalo Barroso (up202207832@up.pt)
3. Guilherme Rego (up202207041@up.pt)

Project Instructions:

Before starting the server, with Ollama's Docker image installed, input the following command on the command prompt:

'''sudo docker run -d -v ollama:/root/.ollama -p 11434:11434 --name ollama14 ollama/ollama'''

This will launch the Ollama AI ChatBot, allowing you to open AI chat rooms.

Then, compile and launch the server with:

'''javac --enable-preview --release <sdk version number (eg. 24)> *.java
   java --enable-preview App server'''

In how many different terminal desired, for clients type:

'''java --enable-preview App client'''

Either Register or Login, and you will join the /general group chat.
Type /help to show all available commands.