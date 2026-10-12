# تقييم مسار Android باستخدام eframe/winit

**التاريخ:** 2026-10-12

**الحالة:** تقييم هندسي أولي؛ لا يثبت تكافؤ الميزات أو جاهزية إنتاجية.

## الخلاصة التنفيذية

الفكرة **صالحة تقنياً كنموذج Android أصلي يعيد استخدام واجهة egui ومحرك العرض الحالي**. توثيق eframe يعرّف ميزتي `android-native-activity` و`android-game-activity`، وwinit 0.30 يشرح ربط Android عبر `android-activity`، بينما يدعم wgpu واجهتي Vulkan وGLES على Android.

لكن هذا لا يعني أن التطبيق الحالي سيعمل حرفياً من دون تغييرات، ولا يثبت خسارة ميزات 0%. التطبيق المكتبي يضم طبقة تشغيل وخدمات نظام مرتبطة بسطح المكتب، وعقود فتح PDF الحالية تعتمد الذاكرة لا `Read + Seek`. المقاربة الأقل مخاطرة هي **إضافة نموذج eframe مستقل وموازٍ**، ثم مقارنته بمسار Kotlin/Compose الحالي قبل اختيار واجهة الإنتاج أو حذف أي مسار.

## ما تؤكده المصادر الرسمية

- **winit 0.30.13** يوفّر Android عبر crate `android-activity`، ويشترط توافق نسخة glue، وبناء مكتبة ديناميكية `cdylib`. يجب اختيار فئة النشاط بميزة `android-native-activity` أو `android-game-activity`. يذكر التوثيق أن Kotlin/Java قد لا يلزم مع NativeActivity الأساسي، لكنه قد يلزم للوصول إلى بعض خدمات المنصة. [توثيق winit Android](https://docs.rs/winit/0.30.13/winit/platform/android/index.html)
- **eframe الحالي** يعرّف ميزتي `android-native-activity` و`android-game-activity` عبر `egui-winit`، إضافة إلى ميزة `wgpu`. هذا يؤكد إمكان بناء واجهة eframe على Android، وليس اكتمال كل تكاملات PdfCraft. [توثيق eframe والميزات](https://docs.rs/eframe/latest/eframe/)
- **wgpu** يذكر Vulkan وGLES كـbackends مدعومين على Android؛ لذلك طبقة الرسم ليست العائق الأساسي بذاتها. يظل التحقق على أجهزة فعلية مهماً بسبب اختلاف تعريفات GPU والذاكرة والطاقة. [ميزات wgpu الرسمية](https://wgpu.rs/doc/wgpu/documentation/features/index.html)
- **Android NDK** يوضح أن NativeActivity تُنشأ وتُدار من إطار Android، وأن callbacks الخاصة بها تعمل على الخيط الرئيسي؛ لا تلغي بذلك دورة حياة Android ولا تحوّل التطبيق إلى عملية يملكها Rust بالكامل. [مرجع Android NDK لـNativeActivity](https://developer.android.com/ndk/reference/group/native-activity)

## المطابقة مع المصدر الفعلي للمستودع

1. مساحة العمل تستخدم `eframe 0.36`، وتطبيق سطح المكتب يبدأ عبر `eframe::run_native` داخل `apps/pdfcraft/src/main.rs`، وليس عبر مدخل Android. كود الإقلاع يهيئ إعدادات ومسارات وتحديثات وطابعات وسلوكاً خاصاً بـmacOS/Linux/Windows، ويعطي نافذة افتراضية بحجم مكتبي مع حد أدنى كبير؛ لا يمكن نقل هذا الإقلاع كما هو إلى شاشة هاتف.
2. `pdfcraft-ui-egui` قابل لإعادة الاستخدام من حيث واجهة المستخدم ومحرك الجلسة، لكنه يعتمد على `pdfcraft-platform` و`rfd`، ويستخدم خدمات غير عامة على Android مثل منتقيات الملفات واكتشاف الخطوط والطابعات والحافظة. كل خدمة تحتاج adapter أو بديل Android مع اختبار سلوكها.
3. ادعاء أن محلل PDF يقبل `Read + Seek` لا ينطبق على واجهة الفتح الحالية: `pdfcraft-cos::Document::open` يستقبل `Arc<Vec<u8>>`، كما أن المستند يحتفظ ببايتات المصدر المشتركة لعمل snapshots والتعديلات. يمكن للجسر قراءة descriptor إلى مخزن بايتات، أو تطوير مصدر عشوائي القراءة بملكية وإغلاق مضبوطين، لكن تمرير FD مباشرة إلى الواجهة الحالية ليس توافقاً بلا تغيير.
4. نواة `pdfcraft-print` تتولى تكوين صفحات الطباعة وفرضها في PDF، بينما إرسال المهمة إلى الطابعة يتم عبر طبقة spooler خارجية. تكامل Android PrintManager يحتاج `PrintDocumentAdapter` وواجهة callbacks/كتابة صفحات، واختبارات للإلغاء وإعادة الطلب؛ ليس مضموناً أن يقتصر على إضافة spooler صغير.
5. قيد `unsafe_code = "forbid"` يطبّق على الشيفرة في الحزمة نفسها ولا يعني أن كل اعتماد يستخدم داخلياً unsafe يفرض إنشاء crate منفصل. عزل تكامل Android في crate مخصص يظل قراراً تنظيمياً جيداً، خصوصاً لنقطة FFI/entrypoint، مع إبقاء أي سماح لـunsafe محلياً ومفسراً.

## التوصية المعمارية

### مرحلة إثبات جدوى معزولة

- إنشاء تطبيق/حزمة Android مستقلة لـeframe، بنوع `cdylib` وميزة Android المناسبة، وربطها مبدئياً بـ`pdfcraft-ui-egui`.
- عدم استبدال أو حذف تطبيق Kotlin/Compose الحالي خلال إثبات الجدوى.
- إظهار شاشة PdfCraft حقيقية صغيرة أولاً، ثم التحقق من فتح المستند عبر SAF، دورة الحياة، استعادة السطح بعد pause/resume، اللمس ولوحة المفاتيح، العرض والذاكرة، ثم التراجع/الحفظ.
- بناء Android عبر NDK/Gradle في CI، ثم تشغيل على محاكي وجهاز فعلي قبل ترقية الحالة إلى مدعوم.

### واجهات المنصة

- **النافذة والرسم:** eframe/winit/wgpu على NativeActivity أو GameActivity. اختر GameActivity إذا سهّل دورة التكامل مع Kotlin وخدمات Android؛ اختر NativeActivity فقط إذا أثبت النموذج أن البساطة تفوق كلفة callbacks المخصصة.
- **الملفات:** SAF يبقى واجهة النظام للمستخدم. أُضيف إلى هذا الفرع adapter أولي يسلّم `ParcelFileDescriptor` إلى JNI، يحوّل نتيجة `detachFd()` إلى `OwnedFd`، ثم يقرأها إلى `Arc<Vec<u8>>` الذي يتطلبه `Document::open`، دون ملف PDF مرحلي لمسار فتح المستند. هذا ليس zero-copy؛ المستند يبقى كاملاً في الذاكرة كما يقتضي عقد المحرك الحالي. يضيف Android هدف `doc_save` افتراضياً محصوراً بجذر workspace من دون كتابة source PDF عند الفتح؛ واستيراد ملفات أدوات Automation ما زال يستخدم المسار المرحلي. لا يُستبدل ذلك بمصدر عشوائي القراءة إلا إذا أثبت القياس حاجة فعلية.
- **الطباعة:** إبقاء تركيب PDF في النواة، وإضافة Android adapter مستقل يستدعي PrintManager عبر Kotlin/Java مع عقد callbacks واضح وإلغاء آمن.
- **معمارية المكونات:** لا تغيّر crates التحليل والتحرير لمجرد تبديل نافذة التطبيق. ضع Android lifecycle/SAF/Print في طبقة منفصلة، واستدعِ نفس `pdfcraft-engine` وأدواته؛ لا تعتبر عرض الشاشة وحده دليلاً على اكتمال الواجهة أو التكافؤ.

## خارطة التنفيذ وبوابات القبول

1. **بوابة البناء:** تشغيل eframe على `aarch64-linux-android` وإنتاج APK قابل للتثبيت؛ تسجيل ميزات Cargo والـABIّات الفعلية.
2. **بوابة الواجهة:** التحقق من الشاشات الصغيرة، إيماءات التكبير/التمرير، لوحة المفاتيح، الإتاحة، اتجاه RTL، وعدم وجود افتراضات نافذة/ماوس.
3. **بوابة المستند:** فتح/حفظ ملفات محلية وSAF، ملفات محمية بكلمة مرور، تصدير نسخة، صلاحيات URI بعد إعادة تشغيل العملية، والملفات الكبيرة.
4. **بوابة الوظائف:** ربط كل سجل commands/tools الحالي بواجهة وتنفيذ Android فعليين، لا بمجرد إظهار الكتالوج، مع اختبارات parity مرجعية.
5. **بوابة النظام:** PrintManager، مشاركة الملفات، اختيار الخطوط، OCR والاعتمادات/النماذج، دورة lifecycle، استهلاك الذاكرة والحرارة.
6. **قرار المعمارية:** لا يُستبدل Compose ولا يُعلن تكافؤ 0% إلا بعد نجاح اختبارات القبول ومصفوفة الميزات على أجهزة Android مستهدفة.

## قرار التنفيذ الحالي

أُضيف مسار فتح SAF عبر Kotlin/JNI إلى نسخة Compose الحالية، لكنه لم يُربط بعد بواجهة eframe ولم يُختبر على جهاز Android. الخطوة التالية تظل نموذج eframe موازياً (spike) مع استبدال كل نقاط `rfd` بخدمات Android قابلة للاختبار؛ لا يكفي إضافة feature في Cargo، ولا تُحذف واجهة Compose قبل اجتياز بوابات القبول.


## أدلة إضافية من المثال الرسمي وrfd

- المثال الرسمي `hello_android` في egui يبني مكتبة `cdylib`، ويصدّر `android_main(AndroidApp)`، ثم يستدعي `eframe::run_native` مع `NativeOptions { android_app: Some(app), .. }`. هذا يثبت أن نقطة ربط eframe عملية ومحددة؛ لا يثبت أن واجهة PdfCraft وخدماتها جاهزة للهاتف. [المثال الرسمي: README](https://github.com/emilk/egui/tree/main/examples/hello_android) · [Cargo.toml](https://raw.githubusercontent.com/emilk/egui/main/examples/hello_android/Cargo.toml) · [lib.rs](https://raw.githubusercontent.com/emilk/egui/main/examples/hello_android/src/lib.rs)
- توثيق `rfd 0.17` يسرد Windows وmacOS وLinux/BSD وWASM فقط، ولا يدرج Android ضمن المنصات المدعومة ولا ضمن قائمة features. في PdfCraft توجد استدعاءات منتقي ملفات مباشرة عبر `rfd::AsyncFileDialog` في عمليات إنشاء/تصدير/توقيع/تعليقات/دمج واستبدال صفحات وغيرها؛ لذلك غياب backend ليس زر فتح واحداً، بل فجوة على مستوى عدة مسارات عمل. [توثيق rfd 0.17](https://docs.rs/rfd/0.17.0/rfd/)
- المثال upstream نفسه يعلّق على `unsafe_code` لأن `#[unsafe(no_mangle)]` نقطة entrypoint، لكنه يذكر أن ذلك خاص بتصدير الرمز في الحزمة؛ لا ينبغي الخلط بينه وبين unsafe الذي تستخدمه مكتبة JNI داخلياً. يمكن أن يبقى هذا الرمز معزولاً ومحدوداً في حزمة Android.
