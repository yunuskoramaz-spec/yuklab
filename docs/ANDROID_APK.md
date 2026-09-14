# YükLab Android 2.2

Test APK'sı: `com.yuklab.nativeapp`, sürüm kodu 5, minimum Android 7.0 (API 24).
Geliştirme anahtarıyla imzalıdır; mağaza yayını değildir.

## Bu sürümde

- Lacivert/beyaz kartlar, yeşil vurgu, sabit beş bölümlü alt menü ve role göre yük veren/taşıyıcı paneli.
- İlanlarda arama, durum filtresi, güzergâh, tonaj, tarih ve ücret kartları; boş liste ve hata sonrası tekrar deneme.
- Üç adımda ilan oluşturma, hesap ve sunucuya özel yerel taslak, konum seçimi ve yayımlamadan önce özet.
- Metinden birden çok ilan taslağı çıkarma: `Kayseri → İzmir | 15 ton | 30.000 TL`. Her satır incelenip ayrı yayımlanır. Bu özellik kurallı metin ayrıştırmadır; yapay zekâ servisi değildir.
- Araç marka/model/yıl, mülkiyet, kapasite, kasa ve güzergâh bilgileri; Lowbed seçeneği.
- Müsait boş araçlar ve nakliyeci rehberi, profil düzenleme, telefonun paylaşılmasına açık tercih.
- Kullanıcıya özel portföy ve 500 karakterlik özel notlar.
- Elle girilen yakıt fiyatı ve masraflarla sefer maliyeti. Vergi/KDV hesabı içermez.
- İlanın alım koordinatı için uygulama içi OpenStreetMap haritası. Yol rotası veya tahmini mesafe üretilmez.
- Açıkça etiketlenmiş tanıtım modu; örnek kayıtlar gerçek veri olarak sunulmaz ve kaydedilmez.
- Önceki sürümün şifreli oturum, belirteç yenileme, HTTPS, teklif, teslimat ve elle konum paylaşımı korunur.

Rehber son 100, ilanlar son 50, portföy son 200 kaydı gösterir; tam sayfalama henüz yoktur.

## Kullanım ve sunucu kurulumu

APK'yı yükleyin. Hesapsız arayüz incelemek için **Örnek ekranları incele** seçeneğini kullanın.
Gerçek kullanım için **Ayarlar ve bağlantı** ekranında çalışan API adresini kaydedin ve bağlantı kontrolünü çalıştırın.
Varsayılan API adresi önceki kontrolde zaman aşımına uğradı; bu çalışmada canlı sunucuya bağlı uçtan uca işlem doğrulanmadı.
APK tek başına API, PostgreSQL veya Redis kurmaz.

Yeni istemciyle birlikte API ve veritabanı da güncellenmelidir. Yeni `User.profile`, `Vehicle.details` ve `Contact` değişiklikleri `0002_mobile_profiles` migration dosyasındadır.
Migration geçmişi düzenli olan veritabanında yedek alıp proje kökünden:

```sh
pnpm install --frozen-lockfile
pnpm --filter @yuklab/database generate
pnpm --filter @yuklab/database exec prisma migrate deploy --schema prisma/schema.prisma
pnpm --filter @yuklab/api build
```

Mevcut `Dockerfile.api` başlangıçta `prisma db push` kullanır. Daha önce bu yöntemle oluşturulmuş veritabanına migration komutunu körlemesine uygulamayın; önce mevcut şemayı migration geçmişiyle eşleştirin. Bu çalışma canlı veritabanına migration veya dağıtım yapmadı.

## Kaynaktan Android derleme

JDK 17, Android SDK Platform 35 ve Build Tools 35.0.0 gerekir. `ANDROID_HOME` SDK dizininizi göstermelidir.

```sh
cd android
./gradlew assembleDebug testDebugUnitTest lintDebug
```

Windows'ta `gradlew.bat` kullanın. APK: `android/app/build/outputs/apk/debug/app-debug.apk`.
Kaynak arşivinin `development-signing/debug.keystore` dosyası yalnızca bu test sürümünün anahtarıdır.
Sonraki test APK'larını aynı imzayla üretmek için mevcut kendi anahtarınızın üzerine yazmadan bu dosyayı ayrı bir geliştirme ortamında `~/.android/debug.keystore` olarak kullanabilirsiniz. Standart debug parolası `android` ve takma adı `androiddebugkey` kullanılır. Üretim imzası olarak kullanmayın ve depoya eklemeyin.
Bu sürüm yeni bir geliştirme anahtarıyla üretildi. 2.2 kurulumu imza hatası verirse eski test sürümünü kaldırmak gerekir; kaldırma cihazdaki yerel taslakları siler.

## Doğrulama ve kalan işler

- Android: 18 test başarılı; bunların 2'si Robolectric ekran/gezinme testidir. Ekran görüntüleri JVM üzerinde oluşturuldu, fiziksel telefon testi değildir.
- API: 10 izole test başarılı; kimlik doğrulama ve veritabanı taklitleriyle gizlilik/yetki ve veri davranışları kontrol edildi. Gerçek PostgreSQL entegrasyon testi değildir.
- API TypeScript kontrolü başarılı. Android lint: hata yok, 19 uyarı (yerelleştirme, çizim, yedekleme API'si ve benzeri).
- Gerçek cihaz, canlı kayıt/giriş, harita ağı ve konum izinleri uçtan uca henüz doğrulanmadı.
- Belge/fotoğraf yükleme, kimlik/firma doğrulama, puanlama yönetimi, gerçek yapay zekâ servisi, e-fatura/e-irsaliye, ödeme, bildirim ve sürekli arka plan takibi tamamlanmış özellikler değildir.

Bağlı GitHub hesabında yazma yetkisi bulunmadığından değişiklikler uzak repoya gönderilmedi. Kaynak arşivi tam kaynakları, değişiklik patch'ini ve doğrulama raporunu içerir.
Leaflet 1.9.4 lisansı uygulama varlıklarında korunmuştur. Harita politikası: https://operations.osmfoundation.org/policies/tiles/
