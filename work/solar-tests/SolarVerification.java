import com.startrail.app.SolarReferenceData;
public class SolarVerification { public static void main(String[] args){ System.out.println("PASS: 12 NASA/JPL fixtures; max angular error="+SolarReferenceData.verify()+" degrees"); } }
