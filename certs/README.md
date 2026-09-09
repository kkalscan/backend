# TLS trust for Tochka

`enter.tochka.com` is signed by Минцифры **Russian Trusted Root/Sub CA**, which is not in the default JVM truststore.

These `.cer` files are imported into the JRE cacerts in `Dockerfile.prod` so `HttpTochkaClient` can create payments.

Source: https://gu-st.ru/content/Other/doc/russian_trusted_root_ca.cer  
and https://gu-st.ru/content/Other/doc/russian_trusted_sub_ca.cer
