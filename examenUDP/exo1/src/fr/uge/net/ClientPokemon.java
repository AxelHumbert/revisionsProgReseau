package fr.uge.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;


import static java.nio.file.StandardOpenOption.*;

public class ClientPokemon {

  private static final Charset UTF8 = StandardCharsets.UTF_8;
  private static final Logger logger = Logger.getLogger(ClientPokemon.class.getName());
  private static final ByteBuffer bufferIn = ByteBuffer.allocateDirect(2048);
  private static final ByteBuffer bufferOut = ByteBuffer.allocateDirect(1024);
  private static final HashMap<String, Integer> characteristics = new HashMap<>();

  private record Pokemon(String name, Map<String, Integer> characteristics) {
    public Pokemon {
      Objects.requireNonNull(name);
      characteristics = Map.copyOf(characteristics);
    }

    @Override
    public String toString() {
      var stringBuilder = new StringBuilder();
      stringBuilder.append(name);
      for (var entry : characteristics.entrySet()) {
        stringBuilder.append(';')
          .append(entry.getKey())
          .append(':')
          .append(entry.getValue());
      }
      return stringBuilder.toString();
    }
  }

  private final String inFilename;
  private final String outFilename;
  private final InetSocketAddress server;
  private final DatagramChannel datagramChannel;

  public static void usage() {
    System.out.println("Usage : ClientPokemon in-filename out-filename host port ");
  }

  public ClientPokemon(String inFilename, String outFilename,
                       InetSocketAddress server) throws IOException {
    this.inFilename = Objects.requireNonNull(inFilename);
    this.outFilename = Objects.requireNonNull(outFilename);
    this.server = server;
    this.datagramChannel = DatagramChannel.open();
  }


  public void launch() throws IOException, InterruptedException {
    try {
      datagramChannel.bind(null);
      // Read all lines of inFilename opened in UTF-8
      var pokemonNames = Files.readAllLines(Path.of(inFilename), UTF8);
      // List of Pokemon to write to the output file
      var pokemons = new ArrayList<Pokemon>();

      // TODO

      for (var pokemon : pokemonNames) {
        var encodedPokemon = UTF8.encode(pokemon);
        var sizePokemonName = encodedPokemon.remaining();
        if (sizePokemonName <= 1020) {
          bufferOut.putInt(sizePokemonName).put(encodedPokemon);
          bufferOut.flip();
          datagramChannel.send(bufferOut, server);
          bufferOut.clear();

          datagramChannel.receive(bufferIn);
          bufferIn.flip();

          var buffer = ByteBuffer.allocate(1024);
          byte trunk = 0;
          var pokemonName = pokemon;

          while (bufferIn.hasRemaining()) {
            var cursor = bufferIn.get();
            if (cursor != trunk) {
              buffer.put(cursor);
            } else {
              buffer.flip();
              var decodedString = UTF8.decode(buffer).toString();
              if (decodedString.equals(pokemon)) {
                pokemonName = decodedString;
              } else {
                characteristics.put(decodedString, bufferIn.getInt());
              }
              buffer.clear();
            }
          }

          pokemons.add(new Pokemon(pokemonName, characteristics));
          bufferIn.clear();
        }
      }

      // Convert the pokemons to strings and write then in the output file
      var lines = pokemons.stream().map(Pokemon::toString).toList();
      Files.write(Paths.get(outFilename), lines, UTF8, CREATE, WRITE, TRUNCATE_EXISTING);
    } finally {
      datagramChannel.close();
    }
  }


  public static void main(String[] args) throws IOException, InterruptedException {
    if (args.length != 4) {
      usage();
      return;
    }

    var inFilename = args[0];
    var outFilename = args[1];
    var server = new InetSocketAddress(args[2], Integer.parseInt(args[3]));

    // Create client with the parameters and launch it
    new ClientPokemon(inFilename, outFilename, server).launch();
  }
}