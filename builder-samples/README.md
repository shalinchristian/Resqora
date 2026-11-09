# Resume builder samples

One complete sample per builder format, written by the same exporters as `POST /api/builder/export`
(`.docx` is the default; `.pdf` is the same form with `"format":"pdf"`).
Use these for the format picker thumbnails so the preview matches the downloaded file.

| File (also `.pdf`) | templateId |
| --- | --- |
| classic-aaron-dsouza.docx | `classic` |
| combination-priya-sharma.docx | `combination` |
| campus-joel-thomas.docx | `campus` |
| centered-lydia-abraham.docx | `centered` |
| header-band-arjun-mehta.docx | `header-band` |

Regenerate after changing the exporter or the sample content
(`resqora-backend/src/test/java/.../service/builder/BuilderSamples.java`):

```
cd resqora-backend
./mvnw test -Dtest='ResumeDocxExporterTest,ResumePdfExporterTest' -Dbuilder.samples.dir=../builder-samples
```
