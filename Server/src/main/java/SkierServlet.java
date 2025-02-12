import com.google.gson.JsonSyntaxException;
import java.io.BufferedReader;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.google.gson.Gson;
import model.LiftRide;

@WebServlet(name = "SkierServlet", value = "/SkierServlet")
public class SkierServlet extends HttpServlet {
  private final Gson gson = new Gson();



  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {
    res.setContentType("text/plain");
    String urlPath = req.getPathInfo();
    BufferedReader body = req.getReader();

    // check we have a URL!
    if (urlPath == null || urlPath.isEmpty()) {
      res.setStatus(HttpServletResponse.SC_NOT_FOUND);
      res.getWriter().write("missing parameters");
      return;
    }

    String[] urlParts = urlPath.split("/");
    // and now validate url path and return the response status code
    // (and maybe also some value if input is valid)

    if (!isUrlValid(urlParts) || !isBodyValid(body)) {
      res.setStatus(HttpServletResponse.SC_NOT_FOUND);
    } else {
      res.setStatus(HttpServletResponse.SC_CREATED);
      // do any sophisticated processing with urlParts which contains all the url params
      // TODO: process url params in `urlParts`
      res.getWriter().write("It works!");
    }
  }


  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {
    res.setContentType("text/plain");
    String urlPath = req.getPathInfo();

    // check we have a URL!
    if (urlPath == null || urlPath.isEmpty()) {
      res.setStatus(HttpServletResponse.SC_NOT_FOUND);
      res.getWriter().write("missing parameters");
      return;
    }

    String[] urlParts = urlPath.split("/");
    // and now validate url path and return the response status code
    // (and maybe also some value if input is valid)

    if (isUrlValid(urlParts)) {
      res.setStatus(HttpServletResponse.SC_OK);
      // do any sophisticated processing with urlParts which contains all the url params
      // TODO: process url params in `urlParts`
      res.getWriter().write("It works!");
    } else {
      res.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }
  }

  private boolean isUrlValid(String[] urlPaths) {
    // TODO: validate the request url path according to the API spec
    // urlPath  = "/1/seasons/2019/day/1/skier/123"
    // urlParts = [, 1, seasons, 2019, day, 1, skier, 123]
//    System.out.println("urlPaths: " + Arrays.toString(urlPaths));
    if (urlPaths.length != 8) {
      return false;
    }
    if (!urlPaths[2].equals("seasons") || !urlPaths[4].equals("days") || !urlPaths[6].equals(
        "skiers")) {
      return false;
    }
    try {
      // Extracting and validating numeric parameters
      int resortID = Integer.parseInt(urlPaths[1]);
      int seasonID = Integer.parseInt(urlPaths[3]);
      int dayID = Integer.parseInt(urlPaths[5]);
      int skierID = Integer.parseInt(urlPaths[7]);
      // Validating against constraints
      return resortID >= 1 && resortID <= 10 && seasonID == 2025 && dayID == 1 && skierID >= 1
          && skierID <= 100000;

    } catch (NumberFormatException e) {
      return false;  // Invalid number format in one of the fields
    }
  }


  private boolean isBodyValid(BufferedReader buffIn) {
    try {
      StringBuilder sb = new StringBuilder();
      String s;
      while ((s = buffIn.readLine()) != null) {
        sb.append(s);
      }
      LiftRide liftRide = gson.fromJson(sb.toString(), LiftRide.class);
      if (liftRide.getTime() < 1 || liftRide.getTime() > 360) return false;
      if (liftRide.getLiftID() < 1 || liftRide.getLiftID() > 40) return false;

//      System.out.println("Body: " + sb);
      return true;
    } catch (IOException | JsonSyntaxException e) {
      throw new RuntimeException(e);
    }
  }
}