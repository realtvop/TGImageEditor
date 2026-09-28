package dev.realtvop.tgimageeditor.nekogram;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Base64;

/** Exact xhdpi icon payloads from the pinned Nekogram revision. */
public final class NekogramEditorIcons {
    public enum Icon { CROP, DRAW, ADJUST, ROTATE, FLIP, TUNE, BLUR, CURVE }

    private NekogramEditorIcons() {}

    public static Drawable drawable(Context context, Icon icon) {
        byte[] bytes = Base64.decode(data(icon), Base64.DEFAULT);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        bitmap.setDensity(320);
        BitmapDrawable drawable = new BitmapDrawable(context.getResources(), bitmap);
        drawable.setTint(Color.WHITE);
        return drawable;
    }

    private static String data(Icon icon) {
        switch (icon) {
            case CROP: return "UklGRjQBAABXRUJQVlA4TCcBAAAvL8ALENVAUrY9bnPl3H/zb2xr6NxbiH+dSRJa6UXwg0ArMdfDjSiylXAmAAFIQAyCvAy0MIcpjGAQI/yTCtu2bajufnLb/BJC2MISXKMmjPARTtwhNNGHwGcePeUhXDPhATuGtLlMqGDNuE5QD5UB0hBoQvZiIKgFzr6Bu8BUVe0x5jQgT1VVnTGmDkgCeqy65xKYTgloeqo550wM59sdsl60uuO3gLlUMPXNz7xDU9/8tOt9NoHqKsO91prmWYZ6lu74N84M1snAu2t3OM0KWA6+TLgfFUFQ1V0B08s62FGG4ZxedCSBow3Nm2cG5T/Kgh1XghHVNEgxrQ336WFtMxME5uwOsHz2TUD35vVWA06jq1+9lGLQiquuQRkUjdeXUDGNXA+eAA==";
            case DRAW: return "UklGRowBAABXRUJQVlA4TH8BAAAvL8ALEGUIBJL2J98gIhK6gAJRzl+i2+tyR0JuI8mR5L/VXFXZnbUnvhFoalvJTgIDmIAIRjCEIdyJYAZCkIEQzgRgfAzcNlLUY+rtznTgDYbEZh6zFmErAIC/duJiLs4OHAA+c5gCsHJqAeDkTSEAuQCqAfE5BEMBtIbhA5KMQXgLj4kEfCMa3G0hAJ2mzvudd3CFqQGzd7iTTCgBUcoJAEsO8gFNO4drgKdBa0Db1WYqQJJsEp+WDo8/JByANDqHC0CZkR+cdAwc0FbkBysdzgDYO/yUCSk5B+BGfnXNbwrMha/w2xoBvrnAbwGAZ5mmLfBmrvkNBchhIEcBotaSB/gkz9H75L+HAqxDCLVask5ZxECtJedoUQl4tZScYwqgdYqwjjujzZ6aZGigiRr6odQLs1+rVQIAxNksbsN5ITcL0EaV3XzzImmwLuYhMwtVQ5PVKYRNhqQ8DShzodJBC86YJ5FyVkPKNOLCjYWCZDhiQHG6Vq6p7Tg2/e+aEgA=";
            case ADJUST: return "UklGRqwAAABXRUJQVlA4TKAAAAAvL8ALEAamjSQ5SvnzJ0XO33u0+6P/M/0jBoBG9w4jlHFEMIA0BjOOkRxEDNw2UpQs8+EjDt7j6c7FWE5aa7GdtC+JZY/oqvDahZbRvPnOmGZBQ3s9yyvrF7oJwsmYwsyBEKuu4m6fgmEWemrMEY324/DvvJ2GzxnS1f7aO5jOGJ5m3esr9K8qHt+IXvMO32q3k+61GJ4wfqn/6uIQ+Swm";
            case ROTATE: return "UklGRlYBAABXRUJQVlA4TEkBAAAvL8ALEIcgFkymnP+RhkAgCW9/oQUCSYL746y4bSRlof8++fh+FP8HY2OgwVDQcBBwBAQFA1YVwJIkpw21IkfB/Y+LbRB64Tui/wzcNlLUSY9hcfYP888G5aZ7RAFZVwG6Gh5LutbVcHhJ23C5VFZRwTZc7vdig0MEvqa9cd59Spzplm4+j1BLFgD8uMZ+NOfIBoCTwrXyh92H2D2QbGjvGontrt9HABAkjnhFfOPKrkQeQJXZmA20nSTDApYktsz2haBrARSR5xTjn53IG0S7LvEFZIEs8BUJcGdfQRUwH+WPj2TBjfEu1McCDmXpm4JUwLBs8lVN0Us/e6LyiP6H1MeJ+jj82XFO6vNIe57SVR6A7DgAiMI8c/A+j/mbPLYvkVqe5Mqf/khGJQ8PA1gf9PI8de11ZFIyknXq9lLLKZ7XQYXPzv8wJgA=";
            case FLIP: return "UklGRqoAAABXRUJQVlA4TJ4AAAAvL8ALEDU4kiTJab7M/y8FSMKMOclNzfVwIwSANmcI5SwBTCiSkIIIIQVu2yjZjpnvDcuvqQAgMFAsQJjQcCwGAA4EfpY5BqLz3MAAT8YBhR0I/EM8agByNuwAPEyFwhRyM3BiwanA0Cygx6yEAoYJ7g5EJxbVeOID2cXeK+sOggmJIP4eDr7nU96R+Hcq/h2s9y7X/icAwFC4pj+gAQ==";
            case TUNE: return "UklGRloBAABXRUJQVlA4TE0BAAAvL8ALELXAbWzbjcNQ5SrcdliJoDI2lgtt7Pn5eP+TKYCMFv+ST/bhYc3HXA83mkhSw1BSUyMBES8EEdhACSIiIRKi48swkCRJRtW6b9rwhuPlo9ugnapdgzV4ULMZV+QV55o7UZOoT3y/54Uqo/sDPCIADHVj24QG652HeDchSeuxjQjxmMbpjK2IayZ61MQiHNOiOuYpzQ7BGvpoTbg0kbuKAauo7wiMdUZOsLHuWQ9Q8nJocULNITfjtpQtYYxrxn3iwIAMGk7U5OnqvKf2qgj4mOT72xnbFDNCyo89WvCbuKSwBb+Ic1DI4a+cztfuATrkFAJliF/HWCRaMA6wIheIBzjLOd929ldhbBGRnLNHhmo5yclVcS9hGnVVOuZobFCkruZWac26mtykz6+u+qdTVx/gB6qr+t9BFYrKdxaqq5qNQTP9/5CP5AYA";
            case BLUR: return "UklGRmQBAABXRUJQVlA4TFgBAAAvL8ALEBJJtu3GbbTl7H/yU1UqEekzxeFpIPLg4Uf/Z/pHCIBNAghiBDEMIo5JBDGGAxzgDCcIIYIm4IXxO6ihU7/oGu9IM/QHIt+NXsFl5Dt5AtB2IJ530fUFvMqB09+BnroBr6pyYK630wXoqMsJ+JtpA7rW1XJA+420AN2r/rsmkG/yANHrZg/geYN1AM26ncBZdg7oU40DcLMEdFWzA2m0DkRv6wFn2WgBWYYJpMkKmGU6IZZFApo2CWhZHPAydjgGmoDeVhvItgFR5gGjzUGH3QRvO6Bll3DagG23IdoC0i5NdMOwGzDbdIIOuwm9bUDYBYy2B9C02sDTVgfcyuGUoRawbTaQFutAaFj0gLMsagO6V9uawC5bbeA8Lc8BvKx1AZG3VgbgZb50ApzUuOp5AHQuu6rB5XEd0w+Xo95W0/+561lvvvuN6Lve5dpDu3cfe9VPFA==";
            case CURVE: return "UklGRioBAABXRUJQVlA4TB4BAAAvL8ALEBJIjiTHjb7s/1/K9kyTYs16JAE2BG7VP/o/079CyLaqR/AgggghhhiORxQhRBFFDDG8HwO3jRSlR8twnX3EA/lXCSseeOG98QweA39cH8Kvs8DgMsJGujquNxNf/2iHG6XFlLBhTLf4MNiSMIkZ4E5gSs2ukPIaycVsCVt5ccUlD8OShGGkd1g1qRXj0p1xANZS7joi0skRVE2pcqmScxmFBXc9aWE+F/QIrPukVZfdpZy6/a1VOXkBR2oXwObXE7FxaV5KFAbh4p4gfKf4Fztl7U52ihN2caUKjG0oBRlbMgom1/ZdUueG1ZRqZsrlycTp5KQoXNjM8XckYVhW0RnbJTWSd11Gk2X5NU14Ll50VMc226vFv1YB";
        }
        throw new AssertionError(icon);
    }
}
